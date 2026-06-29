# 后端工具管理数据模型与 API — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建可持久化的工具管理系统，支持工具的启用/禁用和配置管理（含 API Key 明文存储）。

**Architecture:** 新增 `ToolConfig` JPA 实体 + Repository，重写 `ToolService` 从 DB 读取而非硬编码，在 `ToolController` 中新增 PUT/PATCH 端点。ToolDefinition DTO 扩展 `enabled` 和 `configJson` 字段。DataInitializer 中初始化 6 个种子工具。

**Tech Stack:** Java 17, Spring Boot 3, JPA/H2, Lombok, JUnit 5 + Mockito

---

### Task 1: ToolConfig JPA 实体

**Files:**
- Create: `EcomAgents/src/main/java/cafe/snails/ecomagents/model/ToolConfig.java`

- [ ] **Step 1: 创建 ToolConfig 实体类**

```java
package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tool_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolConfig {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String name = "";

    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String description = "";

    @Column(length = 50)
    @Builder.Default
    private String category = "";

    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "config_json", columnDefinition = "TEXT")
    @Builder.Default
    private String configJson = "";
}
```

- [ ] **Step 2: 创建 ToolConfigRepository**

**Files:**
- Create: `EcomAgents/src/main/java/cafe/snails/ecomagents/repository/ToolConfigRepository.java`

```java
package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ToolConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolConfigRepository extends JpaRepository<ToolConfig, String> {
    List<ToolConfig> findByEnabledTrue();
}
```

### Task 2: 扩展 ToolDefinition DTO

**Files:**
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/dto/ToolDefinition.java`

- [ ] **Step 1: 添加 enabled 和 configJson 字段**

```java
package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {
    private String id;
    private String name;
    private String description;
    private String category;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private String configJson = "";
}
```

### Task 3: 在 DataInitializer 中初始化种子工具

**Files:**
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/config/DataInitializer.java`

- [ ] **Step 1: 注入 ToolConfigRepository 并添加种子数据**

```java
// 新增注入
private final ToolConfigRepository toolConfigRepository;

// 在 run() 方法的种子数据块末尾添加：
initTools();
```

添加新方法：

```java
private void initTools() {
    if (toolConfigRepository.count() > 0) return;
    log.info("初始化工具种子数据...");
    toolConfigRepository.saveAll(List.of(
        ToolConfig.builder().id("web_search").name("网页搜索")
            .description("搜索互联网获取最新信息").category("web").enabled(true).build(),
        ToolConfig.builder().id("image_generation").name("图片生成")
            .description("根据文字描述生成图片").category("media").enabled(true).build(),
        ToolConfig.builder().id("browser_automation").name("浏览器自动化")
            .description("自动浏览网页并提取内容").category("browser").enabled(true).build(),
        ToolConfig.builder().id("file_operation").name("文件操作")
            .description("读取和写入本地文件").category("terminal_files").enabled(true).build(),
        ToolConfig.builder().id("code_execution").name("代码执行")
            .description("运行 Python / JavaScript 等代码片段").category("terminal_files").enabled(true).build(),
        ToolConfig.builder().id("memory_read").name("记忆读取")
            .description("读取持久化的对话记忆").category("memory").enabled(true).build()
    ));
    log.info("工具种子数据初始化完成");
}
```

完整编译后的 imports：

```java
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import java.util.List;
```

`ToolConfigRepository` 需要同时注入到构造器和 DataInitializer 的 `@RequiredArgsConstructor` 中。

### Task 4: 重写 ToolService

**Files:**
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/service/ToolService.java`

- [ ] **Step 1: 完整重写为 DB 驱动实现**

```java
package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolConfigRepository toolConfigRepository;

    /**
     * 获取系统所有可用工具。
     */
    public ApiResponse<List<ToolDefinition>> listTools() {
        List<ToolConfig> configs = toolConfigRepository.findAll();
        List<ToolDefinition> definitions = configs.stream()
                .map(this::toDefinition)
                .toList();
        return ApiResponse.success(definitions);
    }

    /**
     * 根据工具 ID 列表筛选出匹配的工具定义。
     */
    public List<ToolDefinition> getToolsByIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) return List.of();
        return toolConfigRepository.findAllById(toolIds).stream()
                .map(this::toDefinition)
                .toList();
    }

    /**
     * 更新工具名称和描述。
     */
    @Transactional
    public ApiResponse<ToolDefinition> updateTool(String id, ToolDefinition update) {
        return toolConfigRepository.findById(id)
                .map(config -> {
                    if (update.getName() != null) config.setName(update.getName());
                    if (update.getDescription() != null) config.setDescription(update.getDescription());
                    if (update.getCategory() != null) config.setCategory(update.getCategory());
                    ToolConfig saved = toolConfigRepository.save(config);
                    return ApiResponse.success("更新成功", toDefinition(saved));
                })
                .orElse(ApiResponse.error(404, "工具不存在"));
    }

    /**
     * 切换工具的启用/禁用状态。
     */
    @Transactional
    public ApiResponse<ToolDefinition> toggleTool(String id) {
        return toolConfigRepository.findById(id)
                .map(config -> {
                    config.setEnabled(!config.getEnabled());
                    ToolConfig saved = toolConfigRepository.save(config);
                    return ApiResponse.success(
                            saved.getEnabled() ? "工具已启用" : "工具已禁用",
                            toDefinition(saved));
                })
                .orElse(ApiResponse.error(404, "工具不存在"));
    }

    /**
     * 保存工具的配置 JSON（如 API Key 等）。
     */
    @Transactional
    public ApiResponse<ToolDefinition> saveToolConfig(String id, String configJson) {
        return toolConfigRepository.findById(id)
                .map(config -> {
                    config.setConfigJson(configJson);
                    ToolConfig saved = toolConfigRepository.save(config);
                    return ApiResponse.success("配置已保存", toDefinition(saved));
                })
                .orElse(ApiResponse.error(404, "工具不存在"));
    }

    private ToolDefinition toDefinition(ToolConfig config) {
        return ToolDefinition.builder()
                .id(config.getId())
                .name(config.getName())
                .description(config.getDescription())
                .category(config.getCategory())
                .enabled(config.getEnabled())
                .configJson(config.getConfigJson())
                .build();
    }
}
```

### Task 5: 扩展 ToolController

**Files:**
- Modify: `EcomAgents/src/main/java/cafe/snails/ecomagents/controller/ToolController.java`

- [ ] **Step 1: 添加 PUT、PATCH、PUT config 端点**

```java
package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    @GetMapping("/tools")
    public ApiResponse<List<ToolDefinition>> listTools() {
        return toolService.listTools();
    }

    @PutMapping("/tools/{id}")
    public ApiResponse<ToolDefinition> updateTool(
            @PathVariable String id,
            @RequestBody ToolDefinition update) {
        return toolService.updateTool(id, update);
    }

    @PatchMapping("/tools/{id}/toggle")
    public ApiResponse<ToolDefinition> toggleTool(@PathVariable String id) {
        return toolService.toggleTool(id);
    }

    @PutMapping("/tools/{id}/config")
    public ApiResponse<ToolDefinition> saveToolConfig(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        return toolService.saveToolConfig(id, body.getOrDefault("configJson", ""));
    }
}
```

### Task 6: Unit Tests — ToolServiceTest

**Files:**
- Create: `EcomAgents/src/test/java/cafe/snails/ecomagents/service/ToolServiceTest.java`

- [ ] **Step 1: 创建 ToolServiceTest**

```java
package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolServiceTest {

    @Mock
    private ToolConfigRepository repository;

    private ToolService service;

    private ToolConfig webSearchConfig;
    private ToolConfig disabledConfig;

    @BeforeEach
    void setUp() {
        service = new ToolService(repository);
        webSearchConfig = ToolConfig.builder()
                .id("web_search").name("网页搜索")
                .description("搜索互联网").category("web")
                .enabled(true).configJson("").build();
        disabledConfig = ToolConfig.builder()
                .id("browser_automation").name("浏览器自动化")
                .description("自动浏览网页").category("browser")
                .enabled(false).configJson("").build();
    }

    @Test
    void listTools_shouldReturnAll() {
        when(repository.findAll()).thenReturn(List.of(webSearchConfig, disabledConfig));
        ApiResponse<List<ToolDefinition>> result = service.listTools();
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void getToolsByIds_shouldFilterMatching() {
        when(repository.findAllById(List.of("web_search"))).thenReturn(List.of(webSearchConfig));
        List<ToolDefinition> result = service.getToolsByIds(List.of("web_search"));
        assertEquals(1, result.size());
        assertEquals("网页搜索", result.get(0).getName());
    }

    @Test
    void getToolsByIds_empty_shouldReturnEmpty() {
        List<ToolDefinition> result = service.getToolsByIds(null);
        assertTrue(result.isEmpty());
        result = service.getToolsByIds(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void updateTool_shouldUpdateFields() {
        when(repository.findById("web_search")).thenReturn(Optional.of(webSearchConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        ToolDefinition update = ToolDefinition.builder().name("新网页搜索").description("新描述").build();
        ApiResponse<ToolDefinition> result = service.updateTool("web_search", update);
        assertEquals(200, result.getCode());
        assertEquals("新网页搜索", result.getData().getName());
        assertEquals("新描述", result.getData().getDescription());
    }

    @Test
    void updateTool_notFound_shouldReturn404() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());
        ApiResponse<ToolDefinition> result = service.updateTool("nonexistent", ToolDefinition.builder().build());
        assertEquals(404, result.getCode());
    }

    @Test
    void toggleTool_shouldFlipEnabled() {
        when(repository.findById("web_search")).thenReturn(Optional.of(webSearchConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        ApiResponse<ToolDefinition> result = service.toggleTool("web_search");
        assertFalse(result.getData().getEnabled());
    }

    @Test
    void toggleTool_disabled_shouldBecomeEnabled() {
        when(repository.findById("browser_automation")).thenReturn(Optional.of(disabledConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        ApiResponse<ToolDefinition> result = service.toggleTool("browser_automation");
        assertTrue(result.getData().getEnabled());
    }

    @Test
    void toggleTool_notFound_shouldReturn404() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());
        ApiResponse<ToolDefinition> result = service.toggleTool("nonexistent");
        assertEquals(404, result.getCode());
    }

    @Test
    void saveToolConfig_shouldPersistJson() {
        when(repository.findById("web_search")).thenReturn(Optional.of(webSearchConfig));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        String config = "{\"apiKey\": \"test-key-123\", \"provider\": \"tavily\"}";
        ApiResponse<ToolDefinition> result = service.saveToolConfig("web_search", config);
        assertEquals(200, result.getCode());
        assertEquals(config, result.getData().getConfigJson());
    }

    @Test
    void saveToolConfig_notFound_shouldReturn404() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());
        ApiResponse<ToolDefinition> result = service.saveToolConfig("nonexistent", "{}");
        assertEquals(404, result.getCode());
    }

    @Test
    void toDefinition_shouldMapAllFields() {
        when(repository.findAll()).thenReturn(List.of(webSearchConfig));
        ApiResponse<List<ToolDefinition>> result = service.listTools();
        ToolDefinition def = result.getData().get(0);
        assertEquals("web_search", def.getId());
        assertEquals("网页搜索", def.getName());
        assertEquals("web", def.getCategory());
        assertTrue(def.getEnabled());
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd EcomAgents && ./gradlew test --tests "cafe.snails.ecomagents.service.ToolServiceTest"`
Expected: BUILD SUCCESSFUL

### Task 7: Unit Tests — ToolControllerTest

**Files:**
- Create: `EcomAgents/src/test/java/cafe/snails/ecomagents/controller/ToolControllerTest.java`

- [ ] **Step 1: 创建 ToolControllerTest**

```java
package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolDefinition;
import cafe.snails.ecomagents.service.ToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolControllerTest {

    @Mock
    private ToolService toolService;

    private ToolController controller;
    private ToolDefinition sampleTool;

    @BeforeEach
    void setUp() {
        controller = new ToolController(toolService);
        sampleTool = ToolDefinition.builder()
                .id("web_search").name("网页搜索")
                .description("搜索互联网").category("web")
                .enabled(true).configJson("").build();
    }

    @Test
    void listTools_shouldDelegate() {
        when(toolService.listTools()).thenReturn(ApiResponse.success(List.of(sampleTool)));
        ApiResponse<List<ToolDefinition>> result = controller.listTools();
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void updateTool_shouldDelegate() {
        when(toolService.updateTool(eq("web_search"), any())).thenReturn(ApiResponse.success(sampleTool));
        ApiResponse<ToolDefinition> result = controller.updateTool("web_search", sampleTool);
        assertEquals(200, result.getCode());
    }

    @Test
    void toggleTool_shouldDelegate() {
        ToolDefinition toggled = ToolDefinition.builder().id("web_search").enabled(false).build();
        when(toolService.toggleTool("web_search")).thenReturn(ApiResponse.success(toggled));
        ApiResponse<ToolDefinition> result = controller.toggleTool("web_search");
        assertFalse(result.getData().getEnabled());
    }

    @Test
    void saveToolConfig_shouldDelegate() {
        String config = "{\"apiKey\":\"sk-test\"}";
        ToolDefinition configured = ToolDefinition.builder().id("web_search").configJson(config).build();
        when(toolService.saveToolConfig(eq("web_search"), any())).thenReturn(ApiResponse.success(configured));
        ApiResponse<ToolDefinition> result = controller.saveToolConfig("web_search", Map.of("configJson", config));
        assertEquals(config, result.getData().getConfigJson());
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd EcomAgents && ./gradlew test --tests "cafe.snails.ecomagents.controller.ToolControllerTest"`
Expected: BUILD SUCCESSFUL

### Task 8: 全量测试验证

- [ ] **Step 1: Run all backend tests**

Run: `cd EcomAgents && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit**

```bash
git add EcomAgents/src/main/java/cafe/snails/ecomagents/model/ToolConfig.java \
        EcomAgents/src/main/java/cafe/snails/ecomagents/repository/ToolConfigRepository.java \
        EcomAgents/src/main/java/cafe/snails/ecomagents/dto/ToolDefinition.java \
        EcomAgents/src/main/java/cafe/snails/ecomagents/config/DataInitializer.java \
        EcomAgents/src/main/java/cafe/snails/ecomagents/service/ToolService.java \
        EcomAgents/src/main/java/cafe/snails/ecomagents/controller/ToolController.java \
        EcomAgents/src/test/java/cafe/snails/ecomagents/service/ToolServiceTest.java \
        EcomAgents/src/test/java/cafe/snails/ecomagents/controller/ToolControllerTest.java
git commit -m "feat: add ToolConfig entity with CRUD API for tool management

- New ToolConfig JPA entity for persistent tool definitions
- ToolService refactored from hardcoded list to DB-backed
- PUT/PATCH endpoints for update, toggle, config save
- DataInitializer seeds 6 tools on first startup
- Unit tests for service and controller layers

Closes #1"
```
