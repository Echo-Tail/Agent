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

@Service
@RequiredArgsConstructor
public class KnowledgeUnitParserService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeUnitParserService.class);
    private static final int TEXT_CHUNK_SIZE = 1200;
    private static final int TEXT_CHUNK_OVERLAP = 200;
    private static final int MAX_JSON_UNITS = 10000;

    private final ObjectMapper objectMapper;

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
        List<KnowledgeUnit> units = parseText(doc, fileType);
        log.info("Knowledge document parsed as text units: docId={}, kbId={}, fileName={}, fileType={}, units={}",
                doc.getId(), doc.getKnowledgeBaseId(), doc.getFileName(), fileType, units.size());
        return units;
    }

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

    private KnowledgeUnit toJsonUnit(KnowledgeDocument doc, String fileType, JsonNode node, String path, String jsonUnitType) {
        String content;
        try {
            content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            content = node.toString();
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

    private List<KnowledgeUnit> parseText(KnowledgeDocument doc, String fileType) {
        String normalized = doc.getContent().replace("\r\n", "\n").trim();
        List<KnowledgeUnit> units = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + TEXT_CHUNK_SIZE);
            String chunk = normalized.substring(start, end);
            Map<String, Object> metadata = Map.of("chunkIndex", index);
            units.add(new KnowledgeUnit(
                    doc.getId(),
                    doc.getKnowledgeBaseId(),
                    doc.getFileName(),
                    fileType,
                    "text_chunk",
                    "",
                    chunk,
                    metadata,
                    "chunk:" + index
            ));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - TEXT_CHUNK_OVERLAP, start + 1);
            index++;
        }
        return units;
    }

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
