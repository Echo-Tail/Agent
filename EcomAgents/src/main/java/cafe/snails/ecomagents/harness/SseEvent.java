package cafe.snails.ecomagents.harness;

/**
 * SSE 事件类型常量。
 */
public final class SseEvent {

    public static final String TYPE_REASONING = "reasoning";
    public static final String TYPE_TOKEN = "token";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_FILE = "file";

    private SseEvent() {}
}
