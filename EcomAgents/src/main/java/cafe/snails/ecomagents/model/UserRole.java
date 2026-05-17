package cafe.snails.ecomagents.model;

/**
 * 用户角色枚举。
 * <ul>
 *   <li>{@link #ADMIN} — 系统管理员，拥有全部管理权限</li>
 *   <li>{@link #USER} — 普通用户，使用 Agent 进行对话</li>
 * </ul>
 */
public enum UserRole {

    ADMIN("admin"),
    USER("user");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserRole fromValue(String value) {
        for (UserRole role : values()) {
            if (role.value.equals(value)) return role;
        }
        return USER;
    }
}
