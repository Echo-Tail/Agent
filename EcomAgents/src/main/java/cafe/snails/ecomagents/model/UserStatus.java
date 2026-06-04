package cafe.snails.ecomagents.model;

/**
 * 用户账号状态枚举。
 * <ul>
 *   <li>{@link #ACTIVE} — 正常，可登录使用</li>
 *   <li>{@link #DISABLED} — 已禁用，无法登录</li>
 * </ul>
 */
public enum UserStatus {

    ACTIVE("active"),
    DISABLED("disabled");

    /** 持久化和接口中使用的状态值。 */
    private final String value;

    /** 使用持久化值创建枚举项。 */
    UserStatus(String value) {
        this.value = value;
    }

    /** 返回持久化和接口使用的状态值。 */
    public String getValue() {
        return value;
    }

    /** 在启用和禁用之间切换状态。 */
    public UserStatus toggle() {
        return this == ACTIVE ? DISABLED : ACTIVE;
    }

    /** 根据持久化值解析用户状态，无法识别时默认 ACTIVE。 */
    public static UserStatus fromValue(String value) {
        for (UserStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        return ACTIVE;
    }
}
