package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.GroupMemberDTO;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 群聊业务逻辑层。
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final ChatGroupRepository chatGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAgentRepository groupAgentRepository;
    private final AgentService agentService;
    private final UserRepository userRepository;

    // ===== 群 CRUD =====

    /** 创建群 */
    @Transactional
    public ApiResponse<ChatGroup> createGroup(String name, String avatar, Long userId) {
        ChatGroup group = ChatGroup.builder()
                .name(name)
                .avatar(avatar)
                .createdBy(userId)
                .build();
        group = chatGroupRepository.save(group);

        // 创建者自动成为 CREATOR 成员
        GroupMember creator = GroupMember.builder()
                .groupId(group.getId())
                .userId(userId)
                .role(GroupRole.CREATOR)
                .build();
        groupMemberRepository.save(creator);

        return ApiResponse.success("群创建成功", group);
    }

    /** 获取用户可看到的群列表（自己创建的 + 加入的） */
    public ApiResponse<List<ChatGroup>> listMyGroups(Long userId) {
        // 先查出用户加入的群 ID
        List<Long> groupIds = groupMemberRepository.findByUserId(userId)
                .stream().map(GroupMember::getGroupId).toList();
        List<ChatGroup> groups = chatGroupRepository.findAllById(groupIds);
        return ApiResponse.success(groups);
    }

    /** 获取群详情 */
    public ApiResponse<ChatGroup> getGroup(Long groupId) {
        return chatGroupRepository.findById(groupId)
                .map(group -> ApiResponse.success(group))
                .orElse(ApiResponse.error(404, "群不存在"));
    }

    /** 修改群信息 */
    @Transactional
    public ApiResponse<ChatGroup> updateGroup(Long groupId, String name, String avatar, Long userId) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElse(null);
        if (group == null) return ApiResponse.error(404, "群不存在");
        if (!group.getCreatedBy().equals(userId)) return ApiResponse.error(403, "只有创建者可以修改群信息");

        if (name != null) group.setName(name);
        if (avatar != null) group.setAvatar(avatar);
        group = chatGroupRepository.save(group);
        return ApiResponse.success("群信息已更新", group);
    }

    /** 解散群（仅创建者） */
    @Transactional
    public ApiResponse<Void> disbandGroup(Long groupId, Long userId) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElse(null);
        if (group == null) return ApiResponse.error(404, "群不存在");
        if (!group.getCreatedBy().equals(userId)) return ApiResponse.error(403, "只有创建者可以解散群");

        // 清理关联数据
        groupMemberRepository.deleteByGroupId(groupId);
        groupAgentRepository.deleteByGroupId(groupId);
        chatGroupRepository.delete(group);
        return ApiResponse.success("群已解散", null);
    }

    // ===== 成员管理 =====

    /** 邀请用户加入群 */
    @Transactional
    public ApiResponse<Void> inviteMember(Long groupId, Long targetUserId, Long operatorId) {
        if (!isMember(groupId, operatorId)) return ApiResponse.error(403, "你不在群里，无法邀请");
        if (isMember(groupId, targetUserId)) return ApiResponse.error(400, "该用户已在群中");

        GroupMember member = GroupMember.builder()
                .groupId(groupId)
                .userId(targetUserId)
                .role(GroupRole.MEMBER)
                .build();
        groupMemberRepository.save(member);
        return ApiResponse.success("已邀请用户入群", null);
    }

    /** 踢人（仅创建者） */
    @Transactional
    public ApiResponse<Void> kickMember(Long groupId, Long targetUserId, Long operatorId) {
        ChatGroup group = chatGroupRepository.findById(groupId).orElse(null);
        if (group == null) return ApiResponse.error(404, "群不存在");
        if (!group.getCreatedBy().equals(operatorId)) return ApiResponse.error(403, "只有创建者可以踢人");
        if (targetUserId.equals(group.getCreatedBy())) return ApiResponse.error(400, "不能踢出创建者");

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, targetUserId);
        return ApiResponse.success("已踢出成员", null);
    }

    /** 获取群成员列表（含用户名） */
    public ApiResponse<List<GroupMemberDTO>> listMembers(Long groupId) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        List<GroupMemberDTO> dtos = members.stream().map(m -> {
            String username = userRepository.findById(m.getUserId())
                    .map(u -> u.getUsername())
                    .orElse("用户#" + m.getUserId());
            return GroupMemberDTO.builder()
                    .id(m.getId())
                    .groupId(m.getGroupId())
                    .userId(m.getUserId())
                    .username(username)
                    .role(m.getRole())
                    .joinedAt(m.getJoinedAt())
                    .build();
        }).toList();
        return ApiResponse.success(dtos);
    }

    /** 检查用户是否在群里 */
    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    // ===== Agent 绑定 =====

    /** 拉 Agent 入群 */
    @Transactional
    public ApiResponse<Void> addAgent(Long groupId, Long agentId, Long userId) {
        if (!isMember(groupId, userId)) return ApiResponse.error(403, "你不在群里，无法操作");
        if (groupAgentRepository.existsByGroupIdAndAgentId(groupId, agentId))
            return ApiResponse.error(400, "该 Agent 已在群中");

        // 验证 agent 属于当前用户
        var agentResp = agentService.getAgent(agentId);
        if (agentResp.getCode() != 200) return ApiResponse.error(404, "Agent 不存在");
        var agent = agentResp.getData();
        if (!agent.getCreatedBy().equals(userId)) return ApiResponse.error(403, "只能拉入自己创建的 Agent");

        GroupAgent ga = GroupAgent.builder()
                .groupId(groupId)
                .agentId(agentId)
                .addedBy(userId)
                .build();
        groupAgentRepository.save(ga);
        return ApiResponse.success("Agent 已加入群", null);
    }

    /** 从群移除 Agent */
    @Transactional
    public ApiResponse<Void> removeAgent(Long groupId, Long agentId, Long userId) {
        if (!isMember(groupId, userId)) return ApiResponse.error(403, "你不在群里，无法操作");

        var ga = groupAgentRepository.findByGroupIdAndAgentId(groupId, agentId).orElse(null);
        if (ga == null) return ApiResponse.error(404, "该 Agent 不在群中");

        // 只有拉入者或群创建者可移除
        ChatGroup group = chatGroupRepository.findById(groupId).orElse(null);
        if (!ga.getAddedBy().equals(userId) && (group == null || !group.getCreatedBy().equals(userId)))
            return ApiResponse.error(403, "只有拉入者或创建者可以移除 Agent");

        groupAgentRepository.deleteByGroupIdAndAgentId(groupId, agentId);
        return ApiResponse.success("Agent 已从群移除", null);
    }

    /** 获取群绑定的 Agent 列表 */
    public ApiResponse<List<GroupAgent>> listAgents(Long groupId) {
        return ApiResponse.success(groupAgentRepository.findByGroupId(groupId));
    }

    /** 校验用户是群的创建者 */
    public boolean isCreator(Long groupId, Long userId) {
        return chatGroupRepository.findById(groupId)
                .map(g -> g.getCreatedBy().equals(userId))
                .orElse(false);
    }
}
