package cafe.snails.ecomagents.service.rag;

import cafe.snails.ecomagents.model.KnowledgeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识文档解析器。根据文件类型将文档拆分为知识单元（KnowledgeUnit）。
 * <p>支持三种解析策略：</p>
 * <ul>
 *   <li>文本类（txt/md/pdf/docx）→ parseText()：边界感知滑窗，父子块</li>
 *   <li>JSON → parseJson()：递归解析对象/值，按 JSON 节点拆分</li>
 *   <li>表格类（csv/xlsx）→ parseTabular()：逐行附带列名，行组父块</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class KnowledgeUnitParserService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(KnowledgeUnitParserService.class);
    /** 文本文档子块目标大小。 */
    private static final int TEXT_CHUNK_SIZE = 1200;
    /** 相邻文本子块的重叠字符数。 */
    private static final int TEXT_CHUNK_OVERLAP = 200;
    /** 每个父块聚合的文本子块数量。 */
    private static final int TEXT_PARENT_WINDOW = 2;  // 每 2 个子块组合为一个父块
    /** 每个表格父块聚合的行数。 */
    private static final int TABULAR_PARENT_ROWS = 5;  // 每 5 行组合为一个父块
    /** 单个 JSON 文档最多生成的知识单元数量，防止异常大文件拖垮解析。 */
    private static final int MAX_JSON_UNITS = 10000;
    /** JSON 节点序列化后最大字符数，超过则截断（防止 Ollama embedding 超限） */
    private static final int MAX_JSON_CONTENT_LENGTH = 5000;

    private final ObjectMapper objectMapper;

    /**
     * 根据文档类型选择 JSON、表格或文本解析策略，返回可用于向量索引的知识单元。
     */
    public List<KnowledgeUnit> parse(KnowledgeDocument doc) {
        if (doc == null || doc.getContent() == null || doc.getContent().isBlank()) {
            return List.of();
        }
        String fileType = normalizeType(doc.getFileType(), doc.getFileName());
        if ("json".equals(fileType)) {
            List<KnowledgeUnit> units = parseJson(doc, fileType);
            if (!units.isEmpty()) {
                log.info("Knowledge document parsed as JSON units: docId={}, kbId={}, fileName={}, units={}",
                        doc.getId(), doc.getKnowledgeBaseId(), doc.getFileName(), units.size());
                return units;
            }
            log.warn("Knowledge document JSON parsing produced no units; falling back to text chunks: docId={}, fileName={}",
                    doc.getId(), doc.getFileName());
        }
        if ("csv".equals(fileType) || "xlsx".equals(fileType)) {
            List<KnowledgeUnit> units = parseTabular(doc, fileType);
            log.info("Knowledge document parsed as tabular units: docId={}, kbId={}, fileName={}, fileType={}, units={}",
                    doc.getId(), doc.getKnowledgeBaseId(), doc.getFileName(), fileType, units.size());
            return units;
        }
        List<KnowledgeUnit> units = parseText(doc, fileType);
        log.info("Knowledge document parsed as text units: docId={}, kbId={}, fileName={}, fileType={}, units={}",
                doc.getId(), doc.getKnowledgeBaseId(), doc.getFileName(), fileType, units.size());
        return units;
    }

    /**
     * 将 JSON 文档解析为按数组元素或对象节点组织的知识单元。
     */
    private List<KnowledgeUnit> parseJson(KnowledgeDocument doc, String fileType) {
        try {
            JsonNode root = objectMapper.readTree(doc.getContent());
            List<KnowledgeUnit> units = new ArrayList<>();
            collectJsonUnits(doc, fileType, root, "$", units);
            return units;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 递归收集 JSON 顶层数组元素、对象节点和普通值节点。
     */
    private void collectJsonUnits(KnowledgeDocument doc, String fileType, JsonNode node, String path, List<KnowledgeUnit> units) {
        if (units.size() >= MAX_JSON_UNITS || node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size() && units.size() < MAX_JSON_UNITS; i++) {
                JsonNode child = node.get(i);
                String childPath = path + "[" + i + "]";
                if (child.isObject()) {
                    units.add(toJsonUnit(doc, fileType, child, childPath, "object"));
                } else {
                    units.add(toJsonUnit(doc, fileType, child, childPath, "value"));
                }
                if (child.isContainerNode()) {
                    collectNestedContainerUnits(doc, fileType, child, childPath, units);
                }
            }
            return;
        }
        if (node.isObject()) {
            boolean emittedNested = false;
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext() && units.size() < MAX_JSON_UNITS) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode child = field.getValue();
                String childPath = path + "." + field.getKey();
                if (child.isArray()) {
                    emittedNested = true;
                    collectJsonUnits(doc, fileType, child, childPath, units);
                }
            }
            if (!emittedNested || units.isEmpty()) {
                units.add(toJsonUnit(doc, fileType, node, path, "object"));
            }
        }
    }

    /**
     * 对已作为单元输出的对象继续展开其中的数组字段，保留明细层可检索性。
     */
    private void collectNestedContainerUnits(KnowledgeDocument doc, String fileType, JsonNode node, String path, List<KnowledgeUnit> units) {
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext() && units.size() < MAX_JSON_UNITS) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode child = field.getValue();
            if (child.isArray()) {
                collectJsonUnits(doc, fileType, child, path + "." + field.getKey(), units);
            }
        }
    }

    /**
     * 将单个 JSON 节点转换为知识单元，并写入 JSONPath 和节点类型元数据。
     */
    private KnowledgeUnit toJsonUnit(KnowledgeDocument doc, String fileType, JsonNode node, String path, String jsonUnitType) {
        String content;
        try {
            content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            content = node.toString();
        }
        // Truncate oversized JSON content to prevent Ollama 400 on embedding
        if (content.length() > MAX_JSON_CONTENT_LENGTH) {
            int cutPos = findBoundary(content, 0, MAX_JSON_CONTENT_LENGTH);
            content = content.substring(0, cutPos) + "\n...(truncated)";
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("jsonPath", path);
        metadata.put("jsonUnitType", jsonUnitType);
        return new KnowledgeUnit(
                doc.getId(),
                doc.getKnowledgeBaseId(),
                doc.getFileName(),
                fileType,
                "json_" + jsonUnitType,
                resolveJsonTitle(node),
                content,
                metadata,
                path
        );
    }

    /**
     * 从常见业务字段中提取 JSON 单元标题，提升检索结果可读性。
     */
    private String resolveJsonTitle(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "";
        }
        for (String field : List.of("title", "name", "productName", "product_name", "sku", "id", "asin")) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                return field + ": " + value.asText();
            }
        }
        return "";
    }

    /**
     * 按边界感知滑窗拆分文本，并为每个子块生成父块上下文。
     */
    private List<KnowledgeUnit> parseText(KnowledgeDocument doc, String fileType) {
        String normalized = doc.getContent().replace("\r\n", "\n").trim();
        List<KnowledgeUnit> units = new ArrayList<>();
        int start = 0;
        int index = 0;
        // Build children first
        List<String> childContents = new ArrayList<>();
        while (start < normalized.length()) {
            int rawEnd = Math.min(normalized.length(), start + TEXT_CHUNK_SIZE);
            int end = (rawEnd == normalized.length()) ? rawEnd : findBoundary(normalized, start, rawEnd);
            childContents.add(normalized.substring(start, end));
            if (end == normalized.length()) break;
            start = Math.max(end - TEXT_CHUNK_OVERLAP, start + 1);
        }

        // Group children into parent windows and create KnowledgeUnits
        for (int i = 0; i < childContents.size(); i++) {
            String child = childContents.get(i);
            // Build parent content: merge TEXT_PARENT_WINDOW consecutive children
            int parentStart = (i / TEXT_PARENT_WINDOW) * TEXT_PARENT_WINDOW;
            int parentEnd = Math.min(parentStart + TEXT_PARENT_WINDOW, childContents.size());
            String parentContent = String.join("\n\n", childContents.subList(parentStart, parentEnd));
            String parentLocation = "parent:" + (parentStart / TEXT_PARENT_WINDOW);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("chunkIndex", i);
            metadata.put("parentContent", parentContent);
            metadata.put("parentLocation", parentLocation);
            units.add(new KnowledgeUnit(
                    doc.getId(),
                    doc.getKnowledgeBaseId(),
                    doc.getFileName(),
                    fileType,
                    "text_chunk",
                    "",
                    child,
                    metadata,
                    "chunk:" + i,
                    parentContent,
                    parentLocation
            ));
        }
        return units;
    }

    /**
     * 解析表格类文档（CSV / XLSX），每行作为一个 KnowledgeUnit，
     * 格式：表头作为行前缀，每行附带列名。
     * <p>示例输出：</p>
     * <pre>
     * 列头：产品名 | 价格 | 库存
     * --- 行 1 ---
     * 产品名：iPhone 15
     * 价格：5999
     * 库存：120
     * </pre>
     */
    private List<KnowledgeUnit> parseTabular(KnowledgeDocument doc, String fileType) {
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            return List.of();
        }
        String content = doc.getContent();
        List<KnowledgeUnit> units = new ArrayList<>();
        String[] lines = content.split("\n");

        // Find header row (starts with "列头：")
        String headerLine = null;
        int headerIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("列头：")) {
                headerLine = lines[i].substring(3).trim();
                headerIndex = i;
                break;
            }
        }

        String[] headers = headerLine != null ? headerLine.split(" \\| ") : new String[0];
        StringBuilder current = new StringBuilder();
        List<String> rows = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Row marker lines create new units
            if (line.startsWith("--- 行 ") && line.endsWith(" ---")) {
                if (!current.isEmpty()) {
                    rows.add(current.toString().trim());
                    current = new StringBuilder();
                }
                continue;
            }
            if (i == headerIndex) continue;
            current.append(line).append("\n");
        }
        if (!current.isEmpty()) {
            rows.add(current.toString().trim());
        }

        // Build parent groups and create units
        for (int i = 0; i < rows.size(); i++) {
            String rowContent = rows.get(i);
            int parentStart = (i / TABULAR_PARENT_ROWS) * TABULAR_PARENT_ROWS;
            int parentEnd = Math.min(parentStart + TABULAR_PARENT_ROWS, rows.size());
            String parentContent = String.join("\n\n", rows.subList(parentStart, parentEnd));
            String parentLocation = "parent_tabular:" + (parentStart / TABULAR_PARENT_ROWS);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("rowIndex", i);
            metadata.put("parentContent", parentContent);
            metadata.put("parentLocation", parentLocation);

            units.add(new KnowledgeUnit(
                    doc.getId(),
                    doc.getKnowledgeBaseId(),
                    doc.getFileName(),
                    fileType,
                    "tabular_row",
                    "",
                    rowContent,
                    metadata,
                    "row:" + i,
                    parentContent,
                    parentLocation
            ));
        }

        return units;
    }

    /**
     * 构造单行表格知识单元；保留给旧解析路径或测试复用。
     */
    private KnowledgeUnit buildTabularUnit(KnowledgeDocument doc, String fileType, String content, int index) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("rowIndex", index);
        return new KnowledgeUnit(
                doc.getId(),
                doc.getKnowledgeBaseId(),
                doc.getFileName(),
                fileType,
                "tabular_row",
                "",
                content,
                metadata,
                "row:" + index
        );
    }

    /**
     * 从起始位置往后找最近的自然边界（段落/句子结尾）。
     * 优先级：段落边界 &gt; 行边界 &gt; 句子边界 &gt; 保底位置。
     *
     * @param text  全文
     * @param start 搜索起始位置
     * @param limit 搜索上限（不超此位置）
     * @return 边界位置
     */
    private static int findBoundary(String text, int start, int limit) {
        int searchFrom = Math.min(limit, text.length());

        // 1. 段落边界（空行）
        for (int i = searchFrom; i > start; i--) {
            if (i >= 2 && text.charAt(i - 1) == '\n' && text.charAt(i - 2) == '\n') {
                return i;
            }
        }
        // 2. 行边界
        for (int i = searchFrom; i > start; i--) {
            if (text.charAt(i - 1) == '\n') {
                return i;
            }
        }
        // 3. 句子边界（中英文句号、感叹号、问号、分号）
        for (int i = searchFrom; i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == ';' || c == '；') {
                return i;
            }
        }
        // 4. 保底：如果离 limit 超过 100 字符，用 limit；否则用 start+1 防止死循环
        return (limit - start > 100) ? limit : start + 1;
    }

    /**
     * 规范化文件类型；优先使用显式 fileType，缺失时从文件扩展名推断。
     */
    private String normalizeType(String fileType, String fileName) {
        if (fileType != null && !fileType.isBlank()) {
            return fileType.toLowerCase();
        }
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
