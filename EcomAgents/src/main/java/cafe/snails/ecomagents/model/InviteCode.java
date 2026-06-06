package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 邀请码实体，映射 invite_codes 表。
 * <p>用于用户注册时的身份校验，支持批量生成和一键使用标记。</p>
 */
@Entity
@Table(name = "invite_codes")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteCode {
    /** 邀请码字符串（主键） */
    @Id
    @Column(nullable = false, length = 50)
    private String code;

    /** 是否已被使用 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    /** 使用该邀请码的用户名 */
    @Column(length = 50)
    private String usedBy;

    /** 使用该邀请码的用户 ID */
    private Long usedByUserId;

    /** 创建日期 */
    @Column(nullable = false)
    private LocalDate createdAt;
}
