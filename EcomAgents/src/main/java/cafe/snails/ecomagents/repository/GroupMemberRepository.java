package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 群成员仓库。
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    /** 查询群的所有成员 */
    List<GroupMember> findByGroupId(Long groupId);
    /** 查询用户加入的所有群 ID */
    List<GroupMember> findByUserId(Long userId);
    /** 查询某个用户在群里的角色 */
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    /** 检查用户是否在群里 */
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    /** 删除某个群的某个成员 */
    void deleteByGroupIdAndUserId(Long groupId, Long userId);
    /** 删除群的所有成员（解散群时使用） */
    void deleteByGroupId(Long groupId);
}
