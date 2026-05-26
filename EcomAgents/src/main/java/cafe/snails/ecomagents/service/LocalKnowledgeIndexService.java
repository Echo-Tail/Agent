package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class LocalKnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeIndexService.class);
    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;
    private static final long WAIT_FOR_REBUILD_MILLIS = 5000;

    private final KnowledgeDocumentRepository docRepository;
    private final LlmConfig llmConfig;

    private final Map<Long, KnowledgeIndex> indexes = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Void>> rebuildTasks = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> requestedVersions = new ConcurrentHashMap<>();

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmIndexes() {
        List<Long> kbIds = docRepository.findAll().stream()
                .map(KnowledgeDocument::getKnowledgeBaseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (Long kbId : kbIds) {
            rebuildAsync(kbId);
        }
        if (!kbIds.isEmpty()) {
            log.info("Scheduled warm-up for {} knowledge index(es)", kbIds.size());
        }
    }

    public List<String> searchSimilar(List<Long> kbIds, String queryText, int limit, double threshold) {
        if (kbIds == null || kbIds.isEmpty() || queryText == null || queryText.isBlank()) {
            return List.of();
        }

        List<ScoredChunk> chunks = new ArrayList<>();
        for (Long kbId : kbIds) {
            if (kbId == null) {
                continue;
            }
            KnowledgeIndex index = getOrBuildIndex(kbId);
            if (index == null || !index.available()) {
                continue;
            }
            try {
                RetrieveConfig config = RetrieveConfig.builder()
                        .limit(Math.max(1, limit))
                        .scoreThreshold(threshold)
                        .build();
                List<Document> results = index.knowledge().retrieve(queryText, config).block();
                if (results == null) {
                    continue;
                }
                for (Document result : results) {
                    String content = result.getMetadata() == null ? "" : result.getMetadata().getContentText();
                    if (content == null || content.isBlank()) {
                        continue;
                    }
                    chunks.add(new ScoredChunk(content, result.getScore() == null ? 0.0 : result.getScore()));
                }
            } catch (RuntimeException e) {
                log.warn("Local knowledge retrieval failed for KB {}: {}", kbId, e.getMessage());
            }
        }

        return chunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, limit))
                .map(ScoredChunk::content)
                .toList();
    }

    public void rebuildAsync(Long kbId) {
        if (kbId == null) {
            return;
        }
        requestVersion(kbId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleRebuild(kbId);
                }
            });
            return;
        }
        scheduleRebuild(kbId);
    }

    private void scheduleRebuild(Long kbId) {
        rebuildTasks.computeIfAbsent(kbId, id -> CompletableFuture.runAsync(() -> {
            try {
                rebuildUntilCurrent(id);
            } finally {
                rebuildTasks.remove(id);
            }
        }));
    }

    public void evict(Long kbId) {
        if (kbId == null) {
            return;
        }
        requestVersion(kbId);
        indexes.remove(kbId);
        CompletableFuture<Void> task = rebuildTasks.remove(kbId);
        if (task != null) {
            task.cancel(true);
        }
    }

    private KnowledgeIndex getOrBuildIndex(Long kbId) {
        KnowledgeIndex existing = indexes.get(kbId);
        if (existing != null) {
            return existing;
        }

        requestVersion(kbId);
        CompletableFuture<Void> task = rebuildTasks.computeIfAbsent(kbId, id -> CompletableFuture.runAsync(() -> {
            try {
                rebuildUntilCurrent(id);
            } finally {
                rebuildTasks.remove(id);
            }
        }));

        try {
            task.get(WAIT_FOR_REBUILD_MILLIS, TimeUnit.MILLISECONDS);
            return indexes.get(kbId);
        } catch (Exception e) {
            log.warn("Knowledge index for KB {} is not ready; falling back to text search: {}", kbId, e.getMessage());
            return null;
        }
    }

    private void rebuildUntilCurrent(Long kbId) {
        while (!Thread.currentThread().isInterrupted()) {
            long targetVersion = versionOf(kbId);
            rebuildIndex(kbId, targetVersion);
            if (versionOf(kbId) == targetVersion) {
                return;
            }
        }
    }

    private KnowledgeIndex rebuildIndex(Long kbId, long version) {
        List<KnowledgeDocument> docs = docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(kbId);
        SimpleKnowledge knowledge = createKnowledge();
        int indexedChunks = 0;
        int totalChunks = 0;

        for (KnowledgeDocument doc : docs) {
            if (doc.getContent() == null || doc.getContent().isBlank()) {
                continue;
            }
            List<String> chunks = chunkText(doc.getContent());
            totalChunks += chunks.size();
            for (int i = 0; i < chunks.size(); i++) {
                Document document = toDocument(kbId, doc, i, chunks.get(i));
                try {
                    knowledge.addDocuments(List.of(document)).block();
                    indexedChunks++;
                } catch (RuntimeException e) {
                    log.warn("Skipped embedding chunk {} of document {} in KB {}: {}", i, doc.getId(), kbId, e.getMessage());
                }
            }
        }

        KnowledgeIndex index = new KnowledgeIndex(knowledge, indexedChunks);
        if (versionOf(kbId) != version) {
            log.info("Discarded stale knowledge index for KB {}", kbId);
            return index;
        }
        if (index.available()) {
            indexes.put(kbId, index);
            log.info("Rebuilt knowledge index for KB {} with {}/{} chunk(s)", kbId, indexedChunks, totalChunks);
        } else {
            indexes.remove(kbId);
            log.warn("Knowledge index for KB {} has no usable chunks; text search fallback will be used", kbId);
        }
        return index;
    }

    private long requestVersion(Long kbId) {
        return requestedVersions.computeIfAbsent(kbId, id -> new AtomicLong()).incrementAndGet();
    }

    private long versionOf(Long kbId) {
        return requestedVersions.computeIfAbsent(kbId, id -> new AtomicLong()).get();
    }

    private SimpleKnowledge createKnowledge() {
        OllamaTextEmbedding embeddingModel = OllamaTextEmbedding.builder()
                .baseUrl(resolveEmbeddingBaseUrl())
                .modelName(resolveEmbeddingModel())
                .dimensions(resolveEmbeddingDimension())
                .executionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(Math.max(10, llmConfig.getReadTimeout())))
                        .maxAttempts(1)
                        .build())
                .build();
        VDBStoreBase store = InMemoryStore.builder()
                .dimensions(resolveEmbeddingDimension())
                .build();
        return SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
    }

    private Document toDocument(Long kbId, KnowledgeDocument doc, int chunkIndex, String chunk) {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .content(TextBlock.builder().text(chunk).build())
                .docId(String.valueOf(doc.getId()))
                .chunkId(String.valueOf(chunkIndex))
                .addPayload("kbId", kbId)
                .addPayload("documentId", doc.getId())
                .addPayload("fileName", doc.getFileName())
                .build();
        Document document = new Document(metadata);
        document.setVectorName("kb-" + kbId);
        return document;
    }

    private List<String> chunkText(String content) {
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private String resolveEmbeddingBaseUrl() {
        String configured = llmConfig.getEmbeddingApiUrl();
        return configured == null || configured.isBlank() ? "http://localhost:11434" : configured;
    }

    private String resolveEmbeddingModel() {
        String configured = llmConfig.getEmbeddingModel();
        return configured == null || configured.isBlank() ? "bge-m3:latest" : configured;
    }

    private int resolveEmbeddingDimension() {
        return Math.max(1, llmConfig.getEmbeddingDimension());
    }

    private record KnowledgeIndex(SimpleKnowledge knowledge, int indexedChunks) {
        boolean available() {
            return indexedChunks > 0;
        }
    }

    private record ScoredChunk(String content, double score) {
    }
}
