package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent（AI 助手）CRUD 控制器。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** 获取 Agent 列表 */
    @GetMapping("/agents")
    public ApiResponse<List<Agent>> listAgents() {
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

    /** 创建 Agent */
    @PostMapping("/agents")
    public ApiResponse<Agent> createAgent(@RequestBody Agent agent) {
        return agentService.createAgent(agent);
    }

    /** 更新 Agent */
    @PutMapping("/agents/{id}")
    public ApiResponse<Agent> updateAgent(@PathVariable("id") Long id, @RequestBody Agent agent) {
        return agentService.updateAgent(id, agent);
    }

    /** 删除 Agent */
    @DeleteMapping("/agents/{id}")
    public ApiResponse<Agent> deleteAgent(@PathVariable("id") Long id) {
        return agentService.deleteAgent(id);
    }
}
