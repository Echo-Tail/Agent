package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 用户实体，映射 users 表。
 * <p>支持管理员和普通用户两种角色，账号可被启用/禁用。</p>
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    /** 用户 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，唯一约束 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 电子邮箱 */
    @Column(length = 100)
    private String email;

    /** 登录密码（明文存储，后续应改为加密存储） */
    @Column(nullable = false, length = 100)
    private String password;

    /** 角色：admin / user */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "user";

    /** 账号状态：active / disabled */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

    /** 注册时使用的邀请码 */
    @Column(length = 20)
    private String inviteCode;

    /** 账号创建日期 */
    @Column(nullable = false)
    private LocalDate createdAt;
}
