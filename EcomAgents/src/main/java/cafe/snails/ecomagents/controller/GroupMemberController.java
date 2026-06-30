package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.GroupMemberDTO;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 群成员管理控制器。
 */
@RestController
@RequestMapping("/v1/groups/{groupId}/members")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupService groupService;

    /** 邀请用户入群 */
    @PostMapping
    public ApiResponse<Void> inviteMember(@PathVariable Long groupId,
                                          @RequestBody Map<String, Long> body,
                                          @CurrentUserId Long userId) {
        Long targetUserId = body.get("userId");
        if (targetUserId == null) return ApiResponse.error(400, "缺少 userId");
        return groupService.inviteMember(groupId, targetUserId, userId);
    }

    /** 踢出成员（仅创建者） */
    @DeleteMapping("/{targetUserId}")
    public ApiResponse<Void> kickMember(@PathVariable Long groupId,
                                        @PathVariable Long targetUserId,
                                        @CurrentUserId Long userId) {
        return groupService.kickMember(groupId, targetUserId, userId);
    }

    /** 成员列表 */
    @GetMapping
    public ApiResponse<List<GroupMemberDTO>> listMembers(@PathVariable Long groupId) {
        return groupService.listMembers(groupId);
    }
}
