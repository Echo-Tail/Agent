package cafe.snails.ecomagents.model;

/**
 * Agent / AI 助手状态枚举。
 * <ul>
 *   <li>{@link #ACTIVE} — 启用，用户可见并可对话</li>
 *   <li>{@link #DISABLED} — 停用，前端隐藏且不可对话</li>
 * </ul>
 */
public enum AgentStatus {

    ACTIVE("active"),
    DISABLED("disabled");

    private final String value;

    AgentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AgentStatus fromValue(String value) {
        for (AgentStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        return ACTIVE;
    }
}
