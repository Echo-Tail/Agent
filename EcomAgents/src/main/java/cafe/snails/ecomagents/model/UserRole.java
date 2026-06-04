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

    /** 持久化和接口中使用的角色值。 */
    private final String value;

    /** 使用持久化值创建枚举项。 */
    UserRole(String value) {
        this.value = value;
    }

    /** 返回持久化和接口使用的角色值。 */
    public String getValue() {
        return value;
    }

    /** 根据持久化值解析角色，无法识别时默认 USER。 */
    public static UserRole fromValue(String value) {
        for (UserRole role : values()) {
            if (role.value.equals(value)) return role;
        }
        return USER;
    }
}
