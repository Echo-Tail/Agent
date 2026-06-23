package cafe.snails.ecomagents.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 异步触发请求体，对应 Bright Data POST /datasets/v3/trigger。
 */
@Data
public class BrightDataTriggerRequest {
    /** 待抓取的 URL 列表，例如 [{"url": "https://www.amazon.com/dp/B0ABC12345"}] */
    private List<Map<String, Object>> input;

    /** 数据集 ID，不传则使用默认值 */
    private String datasetId;

    /** 输出字段过滤 */
    private String customOutputFields;

    /** 触发类型：discover_new 或 null */
    private String type;

    /** 发现方式：keyword / best_sellers_url / category_url / location */
    private String discoverBy;

    /** 是否包含错误报告，默认 true */
    private Boolean includeErrors = true;

    /** 每个输入的最大结果数 */
    private Integer limitPerInput;

    /** 总结果数上限 */
    private Integer limitMultipleResults;

    /** 完成时是否发送通知 */
    private Boolean notify;

    /** Webhook 回调地址 */
    private String endpoint;

    /** 返回格式：json / ndjson / jsonl / csv，默认 json */
    private String format = "json";

    /** 前端轮询超时时间（秒），默认 300（5 分钟） */
    private Long timeoutSec = 300L;
}
