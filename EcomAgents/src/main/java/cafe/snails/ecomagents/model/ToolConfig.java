package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * 工具配置实体，映射 tool_configs 表。
 * <p>由管理员管理，定义可供 Agent 使用的系统工具（如网页搜索、图片生成等），
 * 支持启用/停用和 JSON 配置持久化。</p>
 */
@Entity
@Table(name = "tool_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolConfig {
    /** 工具唯一标识，如 web_search、image_generation */
    @Id
    @Column(length = 50)
    private String id;

    /** 工具显示名称 */
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String name = "";

    /** 工具描述 */
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String description = "";

    /** 工具分类：web / media / browser / terminal_files / memory */
    @Column(length = 50)
    @Builder.Default
    private String category = "";

    /** 是否启用 */
    @Builder.Default
    private Boolean enabled = true;

    /** JSON 格式的配置信息 */
    @Column(name = "config_json", columnDefinition = "TEXT")
    @Builder.Default
    private String configJson = "";
}
