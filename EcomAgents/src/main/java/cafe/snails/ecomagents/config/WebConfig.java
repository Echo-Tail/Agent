package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.security.CurrentUserIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置，提供 CORS 跨域支持和自定义参数解析器。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig {

    /** 当前用户 ID 参数解析器，支持控制器方法中使用 @CurrentUserId。 */
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    /** CORS 允许的跨域来源，从配置文件读取，逗号分隔。 */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 注册 MVC 跨域、静态资源和自定义方法参数解析器配置。
     */
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            /** 配置 API 和聊天接口跨域访问规则。 */
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = parseAllowedOrigins();
                registry.addMapping("/v1/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
                registry.addMapping("/chat/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }

            /**
             * 解析配置中的跨域白名单，空配置时仅允许同源。
             */
            private String[] parseAllowedOrigins() {
                if (allowedOrigins == null || allowedOrigins.isBlank()) {
                    return new String[0];
                }
                return allowedOrigins.split("\\s*,\\s*");
            }

            /** 将本地 uploads 目录暴露为 /uploads/** 静态资源。 */
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/uploads/**")
                        .addResourceLocations("file:./uploads/");
            }

            /** 注册 @CurrentUserId 方法参数解析器。 */
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(currentUserIdArgumentResolver);
            }
        };
    }
}
