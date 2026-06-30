package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.GroupAgent;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 群 Agent 绑定控制器。
 */
@RestController
@RequestMapping("/v1/groups/{groupId}/agents")
@RequiredArgsConstructor
public class GroupAgentController {

    private final GroupService groupService;

    /** 拉 Agent 入群 */
    @PostMapping
    public ApiResponse<Void> addAgent(@PathVariable Long groupId,
                                      @RequestBody Map<String, Long> body,
                                      @CurrentUserId Long userId) {
        Long agentId = body.get("agentId");
        if (agentId == null) return ApiResponse.error(400, "缺少 agentId");
        return groupService.addAgent(groupId, agentId, userId);
    }

    /** 从群移除 Agent */
    @DeleteMapping("/{agentId}")
    public ApiResponse<Void> removeAgent(@PathVariable Long groupId,
                                         @PathVariable Long agentId,
                                         @CurrentUserId Long userId) {
        return groupService.removeAgent(groupId, agentId, userId);
    }

    /** 群 Agent 列表 */
    @GetMapping
    public ApiResponse<List<GroupAgent>> listAgents(@PathVariable Long groupId) {
        return groupService.listAgents(groupId);
    }
}
