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

    /** 持久化和接口中使用的状态值。 */
    private final String value;

    /** 使用持久化值创建枚举项。 */
    AgentStatus(String value) {
        this.value = value;
    }

    /** 返回持久化和接口使用的状态值。 */
    public String getValue() {
        return value;
    }

    /** 根据持久化值解析状态，无法识别时默认 ACTIVE。 */
    public static AgentStatus fromValue(String value) {
        for (AgentStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        return ACTIVE;
    }
}
