package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 邀请码数据访问层。
 */
public interface InviteCodeRepository extends JpaRepository<InviteCode, String> {
    /** 查找指定 code 且未使用的邀请码 */
    Optional<InviteCode> findByCodeAndUsedFalse(String code);
    /** 统计未使用的邀请码数量 */
    long countByUsedFalse();
}
