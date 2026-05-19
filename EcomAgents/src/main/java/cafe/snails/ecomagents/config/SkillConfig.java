package cafe.snails.ecomagents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 技能导入配置，绑定 application.properties 中 skill.* 前缀的配置项。
 */
@Configuration
@ConfigurationProperties(prefix = "skill")
public class SkillConfig {

    /** gh-proxy 加速地址，用于 git clone 加速 GitHub 仓库下载 */
    private String ghProxyUrl = "https://gh-proxy.org";

    public String getGhProxyUrl() { return ghProxyUrl; }
    public void setGhProxyUrl(String ghProxyUrl) { this.ghProxyUrl = ghProxyUrl; }
}
