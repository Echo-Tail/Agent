package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolAvailability;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.AgentService;
import cafe.snails.ecomagents.service.AgentToolAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Agent（AI 助手）CRUD 控制器。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentToolAvailabilityService toolAvailabilityService;

    /**
     * 获取 Agent 列表。
     * @param scope my-仅自己创建的 / plaza-其他人创建的 / 不传-全部
     */
    @GetMapping("/agents")
    public ApiResponse<List<Agent>> listAgents(@RequestParam(value = "scope", required = false) String scope,
                                                @CurrentUserId Long userId) {
        if (scope != null) {
            return agentService.listAgents(userId, scope);
        }
        return agentService.listAgents();
    }

    /** 获取或初始化系统 Agent */
    @GetMapping("/agents/system")
    public ApiResponse<Agent> getSystemAgent() {
        return agentService.getOrInitSystemAgent();
    }

    /** 获取单个 Agent 详情 */
    @GetMapping("/agents/{id}")
    public ApiResponse<Agent> getAgent(@PathVariable("id") Long id) {
        return agentService.getAgent(id);
    }

    /** 查询指定 Agent 的网页搜索工具可用性。 */
    @GetMapping("/agents/{id}/web-search-availability")
    public ApiResponse<ToolAvailability> getWebSearchAvailability(@PathVariable("id") Long id) {
        return ApiResponse.success(toolAvailabilityService.getWebSearchAvailability(id));
    }

    /** 创建 Agent */
    @PostMapping("/agents")
    public ApiResponse<Agent> createAgent(@Valid @RequestBody Agent agent,
                                          @CurrentUserId Long userId) {
        return agentService.createAgent(agent, userId);
    }

    /** 更新 Agent（仅创建者或管理员可操作） */
    @PutMapping("/agents/{id}")
    public ApiResponse<Agent> updateAgent(@PathVariable("id") Long id,
                                          @Valid @RequestBody Agent agent,
                                          @CurrentUserId Long userId) {
        return agentService.updateAgent(id, agent, userId);
    }

    /** 删除 Agent（仅创建者或管理员可操作） */
    @DeleteMapping("/agents/{id}")
    public ApiResponse<Agent> deleteAgent(@PathVariable("id") Long id,
                                          @CurrentUserId Long userId) {
        return agentService.deleteAgent(id, userId);
    }

    /** 上传 Agent 头像（仅创建者或管理员可操作） */
    @PostMapping("/agents/{id}/avatar")
    public ApiResponse<String> uploadAvatar(@PathVariable("id") Long id,
                                            @RequestParam("file") MultipartFile file,
                                            @CurrentUserId Long userId) {
        return agentService.uploadAvatar(id, file, userId);
    }
}
