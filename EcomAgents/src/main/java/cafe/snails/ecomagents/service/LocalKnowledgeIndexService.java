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

/**
 * 本地知识索引服务，管理每个知识库（KB）的 SimpleKnowledge 实例（Ollama 嵌入 + InMemoryStore）。
 * <p>职责：</p>
 * <ul>
 *   <li>构建和重建向量索引（文档 → 知识单元 → embedding → InMemoryStore）</li>
 *   <li>提供稠密向量检索（searchSimilarDetailed）</li>
 *   <li>支持父子块机制：索引子块内容，检索时返回父块</li>
 *   <li>在应用启动时异步重建所有索引</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LocalKnowledgeIndexService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeIndexService.class);
    /** 首次查询触发索引构建时最多等待的毫秒数，超时后走文本检索降级。 */
    private static final long WAIT_FOR_REBUILD_MILLIS = 750;

    /** 知识文档仓库，用于按知识库加载待索引文档。 */
    private final KnowledgeDocumentRepository docRepository;
    /** LLM/RAG 配置，提供 embedding 地址、模型、维度和超时参数。 */
    private final LlmConfig llmConfig;
    /** 知识单元解析器，将原始文档拆分为可索引的父子块。 */
    private final KnowledgeUnitParserService knowledgeUnitParserService;

    /** 每个知识库当前可用的内存向量索引。 */
    private final Map<Long, KnowledgeIndex> indexes = new ConcurrentHashMap<>();
    /** 每个知识库正在执行的重建任务，用于合并重复重建请求。 */
    private final Map<Long, CompletableFuture<Void>> rebuildTasks = new ConcurrentHashMap<>();
    /** 每个知识库最新请求版本，用于丢弃过期的异步重建结果。 */
    private final Map<Long, AtomicLong> requestedVersions = new ConcurrentHashMap<>();

    /**
     * 应用启动完成后异步预热所有已存在知识库的向量索引。
     */
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

    /**
     * 对指定知识库执行向量检索，只返回命中的文本片段。
     *
     * @param kbIds 知识库 ID 列表
     * @param queryText 查询文本
     * @param limit 返回片段上限
     * @param threshold 相似度阈值
     * @return 命中的知识片段文本
     */
    public List<String> searchSimilar(List<Long> kbIds, String queryText, int limit, double threshold) {
        return searchSimilarDetailed(kbIds, queryText, limit, threshold).chunks();
    }

    /**
     * 对指定知识库执行稠密向量检索（余弦相似度）。
     * 检索子块内容，命中后若有父块（parentContent payload）则返回父块。
     *
     * @param kbIds     知识库 ID 列表
     * @param queryText 用户查询文本
     * @param limit     返回上限
     * @param threshold 相似度阈值
     * @return 检索结果（含 chunks、scores、来源标记、状态信息）
     */
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
                    // Use parent content if available (Parent-Child chunking)
                    String parentContent = (String) result.getMetadata().getPayloadValue("parentContent");
                    String chunkContent = (parentContent != null && !parentContent.isBlank()) ? parentContent : content;
                    chunks.add(new ScoredChunk(chunkContent, result.getScore() == null ? 0.0 : result.getScore()));
                }
            } catch (RuntimeException e) {
                degraded = true;
                timedOut = timedOut || isTimeout(e);
                log.warn("Local knowledge retrieval failed for KB {}: {}", kbId, e.getMessage());
            }
        }

        var sorted = chunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
        List<String> sortedChunks = sorted.stream().map(ScoredChunk::content).toList();
        List<Double> sortedScores = sorted.stream().map(ScoredChunk::score).toList();
        List<String> sortedSources = sorted.stream().map(sc -> "vector").toList();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        int returnedChars = sortedChunks.stream().mapToInt(String::length).sum();
        log.info("Knowledge vector retrieval completed: kbCount={}, chunks={}, degraded={}, timedOut={}, elapsedMs={}, returnedChars={}",
                searchedKbCount, sortedChunks.size(), degraded, timedOut, elapsedMillis, returnedChars);
        return new KnowledgeSearchResult(sortedChunks, sortedSources, sortedScores, degraded, timedOut, searchedKbCount, elapsedMillis, returnedChars);
    }

    /**
     * 请求异步重建指定知识库索引；若当前在事务中，则延迟到事务提交后执行。
     *
     * @param kbId 知识库 ID
     */
    public void rebuildAsync(Long kbId) {
        if (kbId == null) {
            return;
        }
        requestVersion(kbId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 数据库事务提交后再重建索引，确保读取到已提交文档内容。
                 */
                @Override
                public void afterCommit() {
                    scheduleRebuild(kbId);
                }
            });
            return;
        }
        scheduleRebuild(kbId);
    }

    /**
     * 合并同一知识库的重复重建请求，确保同一时间只有一个重建任务运行。
     */
    private void scheduleRebuild(Long kbId) {
        rebuildTasks.computeIfAbsent(kbId, id -> CompletableFuture.runAsync(() -> {
            try {
                rebuildUntilCurrent(id);
            } finally {
                rebuildTasks.remove(id);
            }
        }));
    }

    /**
     * 移除指定知识库的本地索引并取消尚未完成的重建任务。
     *
     * @param kbId 知识库 ID
     */
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

    /**
     * 获取现有索引；不存在时触发同步等待一小段时间的构建，超时则返回空以便降级。
     */
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

    /**
     * 持续重建直到处理到最新请求版本，避免并发更新导致索引落后。
     */
    private void rebuildUntilCurrent(Long kbId) {
        while (!Thread.currentThread().isInterrupted()) {
            long targetVersion = versionOf(kbId);
            rebuildIndex(kbId, targetVersion);
            if (versionOf(kbId) == targetVersion) {
                return;
            }
        }
    }

    /**
     * 重建单个知识库的内存向量索引，并在版本仍然最新时发布到 indexes。
     */
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
                    // Extract Ollama-specific response body for diagnostics
                    Throwable cause = e.getCause();
                    if (cause != null) {
                        String causeName = cause.getClass().getName();
                        if (causeName.contains("OllamaHttpException") || causeName.contains("OllamaHttpClient$OllamaHttpException")) {
                            try {
                                var method = cause.getClass().getMethod("getResponseBody");
                                String body = (String) method.invoke(cause);
                                if (body != null && !body.isBlank()) {
                                    log.warn("Ollama embedding failed for unit {} of doc {} (KB {}): status=400, response={}",
                                            i, doc.getId(), kbId, body.length() > 200 ? body.substring(0, 200) + "..." : body);
                                }
                            } catch (Exception ignored) {
                                // reflection failed, fall through to generic log
                            }
                        }
                    }
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

    /**
     * 递增指定知识库的重建请求版本。
     */
    private long requestVersion(Long kbId) {
        return requestedVersions.computeIfAbsent(kbId, id -> new AtomicLong()).incrementAndGet();
    }

    /**
     * 读取指定知识库当前最新请求版本。
     */
    private long versionOf(Long kbId) {
        return requestedVersions.computeIfAbsent(kbId, id -> new AtomicLong()).get();
    }

    /**
     * 创建 SimpleKnowledge 实例，绑定 Ollama embedding 模型和内存向量存储。
     */
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

    /**
     * 将解析后的知识单元转换为 AgentScope RAG 文档，并写入检索所需 payload。
     */
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
                .addPayload("parentContent", unit.parentContent() != null ? unit.parentContent() : "")
                .addPayload("parentSourceLocation", unit.parentSourceLocation() != null ? unit.parentSourceLocation() : "")
                .build();
        Document document = new Document(metadata);
        document.setVectorName("kb-" + unit.knowledgeBaseId());
        return document;
    }

    /**
     * 格式化参与向量化的文本，保留文件名和位置以提高检索可解释性。
     */
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

    /**
     * 解析 embedding 服务地址，未配置时使用 Ollama 默认地址。
     */
    private String resolveEmbeddingBaseUrl() {
        String configured = llmConfig.getEmbeddingApiUrl();
        return configured == null || configured.isBlank() ? "http://localhost:11434" : configured;
    }

    /**
     * 解析 embedding 模型名称，未配置时使用 bge-m3 默认模型。
     */
    private String resolveEmbeddingModel() {
        String configured = llmConfig.getEmbeddingModel();
        return configured == null || configured.isBlank() ? "bge-m3:latest" : configured;
    }

    /**
     * 解析 embedding 向量维度，并保证至少为 1。
     */
    private int resolveEmbeddingDimension() {
        return Math.max(1, llmConfig.getEmbeddingDimension());
    }

    /**
     * 判断异常链中是否包含超时信号。
     */
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

    /**
     * 知识检索结果，包含文本、来源、相似度和降级状态等诊断信息。
     */
    public record KnowledgeSearchResult(
            List<String> chunks,
            List<String> sources,
            List<Double> scores,
            boolean degraded,
            boolean timedOut,
            int searchedKbCount,
            long elapsedMillis,
            int returnedChars) {

        /**
         * 创建不含来源和分数的检索结果，主要用于空结果或降级场景。
         */
        public KnowledgeSearchResult(List<String> chunks, boolean degraded, boolean timedOut,
                                     int searchedKbCount, long elapsedMillis, int returnedChars) {
            this(chunks, List.of(), List.of(), degraded, timedOut, searchedKbCount, elapsedMillis, returnedChars);
        }

        /**
         * 创建完整检索结果，并对集合和诊断字段进行显式赋值。
         */
        public KnowledgeSearchResult(List<String> chunks, List<String> sources, List<Double> scores,
                                     boolean degraded, boolean timedOut,
                                     int searchedKbCount, long elapsedMillis, int returnedChars) {
            this.chunks = chunks;
            this.sources = sources;
            this.scores = scores;
            this.degraded = degraded;
            this.timedOut = timedOut;
            this.searchedKbCount = searchedKbCount;
            this.elapsedMillis = elapsedMillis;
            this.returnedChars = returnedChars;
        }
    }

    /**
     * 单个知识库的内存索引和已成功索引片段数。
     */
    private record KnowledgeIndex(SimpleKnowledge knowledge, int indexedChunks) {
        /** 索引是否含有可检索片段。 */
        boolean available() {
            return indexedChunks > 0;
        }
    }

    /**
     * 检索命中的文本片段和相似度分数。
     */
    private record ScoredChunk(String content, double score) {
    }
}
