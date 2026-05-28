package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.UnifiedMemberDTO;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.ChatGroup;
import cafe.snails.ecomagents.repository.GroupMessageRepository;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 群 CRUD 控制器。
 */
@RestController
@RequestMapping("/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupMessageRepository groupMessageRepository;

    /** 创建群 */
    @PostMapping
    public ApiResponse<ChatGroup> createGroup(@RequestBody Map<String, String> body,
                                              @CurrentUserId Long userId) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return ApiResponse.error(400, "群名称不能为空");
        return groupService.createGroup(name.trim(), body.get("avatar"), userId);
    }

    /** 我的群列表 */
    @GetMapping
    public ApiResponse<List<ChatGroup>> listMyGroups(@CurrentUserId Long userId) {
        return groupService.listMyGroups(userId);
    }

    /** 群详情 */
    @GetMapping("/{groupId}")
    public ApiResponse<ChatGroup> getGroup(@PathVariable Long groupId) {
        return groupService.getGroup(groupId);
    }

    /** 更新群信息（仅创建者） */
    @PutMapping("/{groupId}")
    public ApiResponse<ChatGroup> updateGroup(@PathVariable Long groupId,
                                              @RequestBody Map<String, String> body,
                                              @CurrentUserId Long userId) {
        return groupService.updateGroup(groupId, body.get("name"), body.get("avatar"), userId);
    }

    /** 解散群（仅创建者） */
    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> disbandGroup(@PathVariable Long groupId,
                                          @CurrentUserId Long userId) {
        return groupService.disbandGroup(groupId, userId);
    }

    /** 上传群头像（仅创建者） */
    @PostMapping("/{groupId}/avatar")
    public ApiResponse<String> uploadAvatar(@PathVariable Long groupId,
                                            @RequestParam("file") MultipartFile file,
                                            @CurrentUserId Long userId) {
        return groupService.uploadAvatar(groupId, file, userId);
    }

    /** 获取统一成员列表（USER + AGENT 合并） */
    @GetMapping("/{groupId}/unified-members")
    public ApiResponse<List<UnifiedMemberDTO>> getUnifiedMembers(@PathVariable Long groupId) {
        return groupService.getUnifiedMembers(groupId);
    }

    /** 获取所有群的未读消息数 */
    @GetMapping("/unread-summary")
    public ApiResponse<List<Map<String, Object>>> getGroupUnreadSummary(@CurrentUserId Long userId) {
        // 获取用户加入的所有群
        var groups = groupService.listMyGroups(userId).getData();
        List<Map<String, Object>> result = groups.stream().map(g -> {
            long count = groupMessageRepository.countUnreadByGroupId(g.getId(), userId);
            return Map.<String, Object>of("groupId", g.getId(), "count", count);
        }).filter(m -> (long) m.get("count") > 0).toList();
        return ApiResponse.success(result);
    }

    /** 标记群聊为已读 */
    @PutMapping("/{groupId}/read")
    @Transactional
    public ApiResponse<Void> markGroupAsRead(@PathVariable Long groupId, @CurrentUserId Long userId) {
        groupMessageRepository.markGroupAsRead(groupId, userId);
        return ApiResponse.success("已标记为已读", null);
    }

    /** 获取当前用户可邀请的 Agent（已创建且未入群的） */
    @GetMapping("/{groupId}/invitable-agents")
    public ApiResponse<List<Agent>> getInvitableAgents(@PathVariable Long groupId,
                                                        @CurrentUserId Long userId) {
        return groupService.getInvitableAgents(groupId, userId);
    }
}
