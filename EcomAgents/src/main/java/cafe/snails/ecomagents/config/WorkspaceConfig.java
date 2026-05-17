package cafe.snails.ecomagents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Workspace 根目录配置，绑定 application.properties 中 workspace.* 前缀的配置项。
 */
@Configuration
@ConfigurationProperties(prefix = "workspace")
public class WorkspaceConfig {

    /** workspace 根目录，默认 ./workspace */
    private String root = "./workspace";

    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }
}
