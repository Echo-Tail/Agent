package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.GroupMemberDTO;
import cafe.snails.ecomagents.dto.UnifiedMemberDTO;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final AgentRepository agentRepository;
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

    /** 上传群头像 */
    @Transactional
    public ApiResponse<String> uploadAvatar(Long groupId, MultipartFile file, Long userId) {
        var groupOpt = chatGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) return ApiResponse.error(404, "群不存在");
        if (!groupOpt.get().getCreatedBy().equals(userId)) return ApiResponse.error(403, "仅创建者可修改群头像");

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!Set.of("jpg", "jpeg", "png", "gif", "webp").contains(ext)) {
            return ApiResponse.error(400, "仅支持 JPG/PNG/GIF/WEBP 格式");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            return ApiResponse.error(400, "头像文件不能超过 2MB");
        }

        try {
            Path uploadPath = Paths.get("./uploads/group-avatars").toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedName = "group-" + groupId + "-" + UUID.randomUUID() + "." + ext;
            Path targetPath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = "/uploads/group-avatars/" + storedName;
            ChatGroup group = groupOpt.get();
            group.setAvatar(avatarUrl);
            chatGroupRepository.save(group);

            return ApiResponse.success("头像上传成功", avatarUrl);
        } catch (IOException e) {
            return ApiResponse.error(500, "头像上传失败: " + e.getMessage());
        }
    }

    /** 获取统一成员列表（USER + AGENT 合并） */
    public ApiResponse<List<UnifiedMemberDTO>> getUnifiedMembers(Long groupId) {
        List<UnifiedMemberDTO> members = new ArrayList<>();

        // 用户成员
        List<GroupMember> userMembers = groupMemberRepository.findByGroupId(groupId);
        for (GroupMember gm : userMembers) {
            User user = userRepository.findById(gm.getUserId()).orElse(null);
            members.add(UnifiedMemberDTO.builder()
                    .id(gm.getId())
                    .memberType("USER")
                    .refId(gm.getUserId())
                    .name(user != null ? user.getUsername() : "未知用户")
                    .avatar(null)
                    .role(gm.getRole().name())
                    .build());
        }

        // Agent 成员
        List<GroupAgent> agentMembers = groupAgentRepository.findByGroupId(groupId);
        for (GroupAgent ga : agentMembers) {
            Agent agent = agentRepository.findById(ga.getAgentId()).orElse(null);
            if (agent == null) continue;
            members.add(UnifiedMemberDTO.builder()
                    .id(ga.getId())
                    .memberType("AGENT")
                    .refId(ga.getAgentId())
                    .name(agent.getName())
                    .avatar(agent.getAvatar())
                    .icon(agent.getIcon())
                    .role("MEMBER")
                    .build());
        }

        return ApiResponse.success(members);
    }

    /** 获取可邀请的 Agent（当前用户创建且未入群的） */
    public ApiResponse<List<Agent>> getInvitableAgents(Long groupId, Long userId) {
        List<Agent> userAgents = agentRepository.findByCreatedByAndIsSystemFalse(userId);
        List<GroupAgent> existingAgents = groupAgentRepository.findByGroupId(groupId);
        Set<Long> existingAgentIds = existingAgents.stream()
                .map(GroupAgent::getAgentId)
                .collect(Collectors.toSet());

        List<Agent> invitable = userAgents.stream()
                .filter(a -> !existingAgentIds.contains(a.getId()))
                .collect(Collectors.toList());

        return ApiResponse.success(invitable);
    }

    /** 校验用户是群的创建者 */
    public boolean isCreator(Long groupId, Long userId) {
        return chatGroupRepository.findById(groupId)
                .map(g -> g.getCreatedBy().equals(userId))
                .orElse(false);
    }
}
