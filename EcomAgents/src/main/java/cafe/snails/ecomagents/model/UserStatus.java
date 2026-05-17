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

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public UserStatus toggle() {
        return this == ACTIVE ? DISABLED : ACTIVE;
    }

    public static UserStatus fromValue(String value) {
        for (UserStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        return ACTIVE;
    }
}
