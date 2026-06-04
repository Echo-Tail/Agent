package cafe.snails.ecomagents.harness;

/**
 * SSE 事件类型常量。
 */
public final class SseEvent {

    /** 推理过程事件，用于传递模型思考或规划片段。 */
    public static final String TYPE_REASONING = "reasoning";
    /** 增量 token 事件，用于流式输出回答正文。 */
    public static final String TYPE_TOKEN = "token";
    /** 工具调用事件，表示模型即将调用某个工具。 */
    public static final String TYPE_TOOL_CALL = "tool_call";
    /** 工具结果事件，表示工具执行完成并返回结果。 */
    public static final String TYPE_TOOL_RESULT = "tool_result";
    /** 流式响应完成事件。 */
    public static final String TYPE_DONE = "done";
    /** 错误事件，用于向前端传递异常信息。 */
    public static final String TYPE_ERROR = "error";
    /** 文件事件，用于传递 Agent 生成或引用的文件信息。 */
    public static final String TYPE_FILE = "file";

    /** 工具类不允许实例化。 */
    private SseEvent() {}
}
