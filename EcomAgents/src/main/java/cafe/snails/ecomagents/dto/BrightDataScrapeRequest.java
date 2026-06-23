package cafe.snails.ecomagents.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 同步抓取请求体，对应 Bright Data POST /datasets/v3/scrape。
 */
@Data
public class BrightDataScrapeRequest {
    /** 待抓取的 URL 列表，例如 [{"url": "https://www.amazon.com/dp/B0ABC12345"}] */
    private List<Map<String, Object>> input;

    /** 数据集 ID，不传则使用默认值 */
    private String datasetId;

    /** 输出字段过滤，例如 "url|about.updated_on" */
    private String customOutputFields;

    /** 是否包含错误报告，默认 true */
    private Boolean includeErrors = true;

    /** 返回格式：json / ndjson / csv，默认 json */
    private String format = "json";
}
