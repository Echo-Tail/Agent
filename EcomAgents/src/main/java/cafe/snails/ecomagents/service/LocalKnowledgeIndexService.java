package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
import cafe.snails.ecomagents.service.rag.KnowledgeUnit;
import cafe.snails.ecomagents.service.rag.KnowledgeUnitParserService;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class LocalKnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeIndexService.class);
    private static final long WAIT_FOR_REBUILD_MILLIS = 750;

    private final KnowledgeDocumentRepository docRepository;
    private final LlmConfig llmConfig;
    private final KnowledgeUnitParserService knowledgeUnitParserService;

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
        return searchSimilarDetailed(kbIds, queryText, limit, threshold).chunks();
    }

    public KnowledgeSearchResult searchSimilarDetailed(List<Long> kbIds, String queryText, int limit, double threshold) {
        long startedAt = System.nanoTime();
        if (kbIds == null || kbIds.isEmpty() || queryText == null || queryText.isBlank()) {
            return new KnowledgeSearchResult(List.of(), false, false, 0, 0, 0);
        }

        List<ScoredChunk> chunks = new ArrayList<>();
        boolean degraded = false;
        boolean timedOut = false;
        int searchedKbCount = 0;
        for (Long kbId : kbIds) {
            if (kbId == null) {
                continue;
            }
            searchedKbCount++;
            KnowledgeIndex index = getOrBuildIndex(kbId);
            if (index == null || !index.available()) {
                degraded = true;
                continue;
            }
            try {
                RetrieveConfig config = RetrieveConfig.builder()
                        .limit(Math.max(1, limit))
                        .scoreThreshold(threshold)
                        .build();
                List<Document> results = index.knowledge()
                        .retrieve(queryText, config)
                        .block(Duration.ofSeconds(Math.max(1, llmConfig.getRagRetrievalTimeout())));
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
                degraded = true;
                timedOut = timedOut || isTimeout(e);
                log.warn("Local knowledge retrieval failed for KB {}: {}", kbId, e.getMessage());
            }
        }

        List<String> sortedChunks = chunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, limit))
                .map(ScoredChunk::content)
                .toList();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        int returnedChars = sortedChunks.stream().mapToInt(String::length).sum();
        log.info("Knowledge vector retrieval completed: kbCount={}, chunks={}, degraded={}, timedOut={}, elapsedMs={}, returnedChars={}",
                searchedKbCount, sortedChunks.size(), degraded, timedOut, elapsedMillis, returnedChars);
        return new KnowledgeSearchResult(sortedChunks, degraded, timedOut, searchedKbCount, elapsedMillis, returnedChars);
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
        int indexedUnits = 0;
        int totalUnits = 0;

        for (KnowledgeDocument doc : docs) {
            if (doc.getContent() == null || doc.getContent().isBlank()) {
                continue;
            }
            List<KnowledgeUnit> units = knowledgeUnitParserService.parse(doc);
            totalUnits += units.size();
            for (int i = 0; i < units.size(); i++) {
                KnowledgeUnit unit = units.get(i);
                Document document = toDocument(unit, i);
                try {
                    knowledge.addDocuments(List.of(document)).block();
                    indexedUnits++;
                } catch (RuntimeException e) {
                    log.warn("Skipped embedding unit {} of document {} in KB {}: {}", i, doc.getId(), kbId, e.getMessage());
                }
            }
        }

        KnowledgeIndex index = new KnowledgeIndex(knowledge, indexedUnits);
        if (versionOf(kbId) != version) {
            log.info("Discarded stale knowledge index for KB {}", kbId);
            return index;
        }
        if (index.available()) {
            indexes.put(kbId, index);
            log.info("Rebuilt knowledge index for KB {} with {}/{} unit(s)", kbId, indexedUnits, totalUnits);
        } else {
            indexes.remove(kbId);
            log.warn("Knowledge index for KB {} has no usable units; text search fallback will be used", kbId);
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

    private Document toDocument(KnowledgeUnit unit, int unitIndex) {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .content(TextBlock.builder().text(formatUnitForRetrieval(unit)).build())
                .docId(String.valueOf(unit.documentId()))
                .chunkId(String.valueOf(unitIndex))
                .addPayload("kbId", unit.knowledgeBaseId())
                .addPayload("documentId", unit.documentId())
                .addPayload("fileName", unit.fileName())
                .addPayload("fileType", unit.fileType())
                .addPayload("unitType", unit.unitType())
                .addPayload("sourceLocation", unit.sourceLocation())
                .build();
        Document document = new Document(metadata);
        document.setVectorName("kb-" + unit.knowledgeBaseId());
        return document;
    }

    private String formatUnitForRetrieval(KnowledgeUnit unit) {
        StringBuilder sb = new StringBuilder();
        sb.append("[source: ").append(unit.fileName());
        if (unit.sourceLocation() != null && !unit.sourceLocation().isBlank()) {
            sb.append(" @ ").append(unit.sourceLocation());
        }
        sb.append("]\n");
        if (unit.title() != null && !unit.title().isBlank()) {
            sb.append(unit.title()).append("\n");
        }
        sb.append(unit.content());
        return sb.toString();
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

    private boolean isTimeout(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record KnowledgeSearchResult(
            List<String> chunks,
            boolean degraded,
            boolean timedOut,
            int searchedKbCount,
            long elapsedMillis,
            int returnedChars) {
    }

    private record KnowledgeIndex(SimpleKnowledge knowledge, int indexedChunks) {
        boolean available() {
            return indexedChunks > 0;
        }
    }

    private record ScoredChunk(String content, double score) {
    }
}
