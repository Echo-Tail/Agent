# RAG 检索算法

## 概述

本系统采用混合检索（Hybrid Retrieval）方案，结合稠密向量检索（Dense）和稀疏关键词检索（Sparse），通过 Reciprocal Rank Fusion (RRF) 融合排序，再经第二遍关键词重排序（Re-ranking），最后注入 LLM 上下文。

支持两种 RAG 模式：

| 模式 | 触发方式 | 适合场景 |
|------|---------|---------|
| **GENERIC** | 自动注入：`HarnessChatService` 将检索结果拼入用户消息 | 默认模式，无需 Agent 额外调用工具 |
| **AGENTIC** | Agent 主动调用 `retrieve_knowledge` 工具 | Agent 自主决定何时检索 |

---

## 数据流

```
用户查询
  ↓
  ├─(GENERIC)── HarnessChatService.enrichWithKnowledge()
  └─(AGENTIC)── RetrieveKnowledgeTool.retrieve_knowledge()
                    ↓
          KnowledgeBaseService.buildKnowledgeContext(kbIds, query)
                    ↓
          ┌─────────────────┐     ┌─────────────────────┐
          │  稠密向量检索    │     │   稀疏关键词检索     │
          │ (Dense Vector)  │     │  (Sparse Keyword)   │
          └────────┬────────┘     └──────────┬──────────┘
                   │                         │
                   └──────────┬──────────────┘
                              ↓
                    RRF 融合 (K=60)
                              ↓
                   关键词重排序 (Re-rank)
                              ↓
                   上下文组装 + 截断 (≤16000 字符)
                              ↓
                      注入 LLM 消息
```

---

## 1. 稠密向量检索（Dense Vector Search）

### 索引构建

```
知识文档 (txt/md/pdf/docx/json/csv/xlsx)
  → KnowledgeUnitParserService.parse()  → 切片策略
  → OllamaTextEmbedding (bge-m3:latest, 1024d)
  → InMemoryStore (暴力余弦相似度)
```

**文件路由与切片策略**：

| 文件类型 | 切片方式 | 子块（检索用） | 父块（上下文用） |
|---------|---------|---------------|----------------|
| `.txt`, `.md`, `.pdf`, `.docx` | 边界感知滑窗（1200 字符，200 重叠） | ~1200 字 | 2 块合并 ~2400 字 |
| `.csv`, `.xlsx` | 逐行提取，附带列名前缀 | 单行 | 5 行一组 |
| `.json` | Jackson 递归解析，每对象/元素一单元 | 单个 JSON 对象 | 同 JSON 对象 |

**边界感知算法**（`findBoundary()`）：

```
从目标切点往前找：
  1. 段落边界 (\n\n)  → 优先
  2. 行边界 (\n)      → 其次
  3. 句子边界 (。！？.!?) → 再次
  4. 硬切 (limit)     → 保底
```

### 检索执行

```
Ollama 对用户查询做 embedding → InMemoryStore 余弦相似度搜索
  → 按 score 降序排列
  → 取 top-N（默认 5，阈值 0.15）
  → 如果有父块内容（parentContent payload），返回父块而非子块
```

---

## 2. 稀疏关键词检索（Sparse Keyword Search）

### 检索流程

```
用户查询
  → extractSearchTerms() 提取关键词（含 CJK 二元/三元/四元组）
  → extractIdentifierTerms() 提取标识符（代码风格）
  ↓
SQL LIKE 搜索 → docRepository.searchByKeywordAndKbIds()
  ↓（如无结果）
读取 KB 全部文档 → docRepository.findByKnowledgeBaseIdIn()
  ↓
KnowledgeUnitParserService.parse() 拆分为知识单元
  ↓
scoreUnit() 评分：
  • 标识符精确匹配 → +1000
  • 关键词命中 → +term.length
  • JSON 单元 → +10 奖励
  ↓
取 Top 75% 分数以上的结果（上限 5 个）
```

### 评分公式

```java
score =
    (identifierMatch ? 1000 : 0)
  + Σ(term.length for each matched term)
  + (jsonUnit ? 10 : 0)
```

---

## 3. RRF 融合（Reciprocal Rank Fusion）

同时运行稠密 + 稀疏检索后，对两个结果列表进行 RRF 融合：

```java
score(item) = 1/(K + rank_dense) + 1/(K + rank_sparse)
```

| 参数 | 值 | 说明 |
|------|-----|------|
| K | 60 | RRF 常数，控制排序敏感度 |
| top-N | 5 | 融合后取前 N 个结果 |

**状态标记**：

| 标记 | 含义 |
|------|------|
| `hybrid_vector_sparse` | 稠密 + 稀疏均有结果（最佳） |
| `vector_search` | 仅稠密有结果 |
| `text_search_fallback` | 稀疏回退（稠密无结果） |
| `degraded_vector_search` | 部分 KB 索引不可用 |
| `vector_timeout_no_fallback` | 稠密超时且稀疏无结果 |

---

## 4. 关键词重排序（Re-ranking）

RRF 融合后，对结果进行第二遍精排：

### 评分维度

| 维度 | 权重 | 说明 |
|------|------|------|
| **关键词覆盖度** | ×2.0 | 唯一命中词数 / 总查询词数 |
| **关键词密度** | ×5.0 | 总命中次数 / chunk 长度 |
| **精确短语加分** | +0.3 | chunk 包含完整查询原文 |

### CJK 处理

对中文字符额外生成二元组（bigram）参与匹配，提高 CJK 文本的命中率。

---

## 5. 上下文组装

```java
header = "\n\nKnowledge retrieval status: hybrid_vector_sparse\n\n"
       + "Use the knowledge context below to answer. ...\n\n"

for each chunk:
    context += "--- chunk N ---\n"
             + truncate(chunk, 3000)  // 单块上限 3000 字
             + "\n\n"

limitContext(context)  // 总上限 16000 字
```

---

## 6. 相关配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `AiModel` 的 `EMBEDDING` 能力 | — | Embedding 地址、模型、凭据和协议 |
| `EMBEDDING.optionsJson.dimension` | 1024 | 向量维度 |
| `rag.search-limit` | 5 | 检索返回上限 |
| `rag.similarity-threshold` | 0.15 | 向量相似度阈值 |
| `rag.max-context-chars` | 16000 | 上下文总上限 |
| `rag.retrieval-timeout` | 8s | 检索超时 |
| `TEXT_CHUNK_SIZE` | 1200 | 文本子块大小 |
| `TEXT_CHUNK_OVERLAP` | 200 | 文本子块重叠 |
| `TEXT_PARENT_WINDOW` | 2 | 每 N 子块合并为一个父块 |
| `TABULAR_PARENT_ROWS` | 5 | 每 N 行合并为一个父块 |
| RRF K 常数 | 60 | Reciprocal Rank Fusion 常数 |

---

## 7. 关键文件

| 文件 | 职责 |
|------|------|
| `KnowledgeBaseService.java` | 编排检索：稠密+稀疏+RRF+重排序+上下文组装 |
| `LocalKnowledgeIndexService.java` | 向量索引管理 + 稠密检索 |
| `KnowledgeUnitParserService.java` | 文档切片（文本/JSON/表格） |
| `KnowledgeUnit.java` | 知识单元数据结构（含父子块字段） |
| `KnowledgeBaseController.java` | 知识库 API（上传/删除/搜索） |
| `RetrieveKnowledgeTool.java` | AGENTIC 模式的检索工具 |
| `HarnessChatService.java` | GENERIC 模式的自动注入 |
| `RagProperties.java` | RAG 检索策略参数 |
| `EmbeddingModelResolver.java` | 从 AiModel 解析向量模型 |
