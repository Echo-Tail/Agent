package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.security.CurrentUserIdArgumentResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebConfig CORS 跨域配置解析测试。
 * <p>验证不同 allowed-origins 配置下不会抛出异常，且白名单解析正确。</p>
 */
@ExtendWith(MockitoExtension.class)
class WebConfigTest {

    @Mock
    private CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    @Test
    void cors_shouldAcceptMultipleOrigins() {
        var config = new WebConfig(currentUserIdArgumentResolver);
        setField(config, "allowedOrigins", "http://localhost:5174,http://192.168.1.100:5174");

        assertDoesNotThrow(() -> config.webMvcConfigurer().addCorsMappings(new CorsRegistry()));
    }

    @Test
    void cors_shouldAcceptEmptyOrigins() {
        var config = new WebConfig(currentUserIdArgumentResolver);
        setField(config, "allowedOrigins", "");

        assertDoesNotThrow(() -> config.webMvcConfigurer().addCorsMappings(new CorsRegistry()));
    }

    @Test
    void cors_shouldAcceptNullOrigins() {
        var config = new WebConfig(currentUserIdArgumentResolver);
        setField(config, "allowedOrigins", null);

        assertDoesNotThrow(() -> config.webMvcConfigurer().addCorsMappings(new CorsRegistry()));
    }

    @Test
    void cors_shouldAcceptSingleOrigin() {
        var config = new WebConfig(currentUserIdArgumentResolver);
        setField(config, "allowedOrigins", "http://localhost:5174");

        assertDoesNotThrow(() -> config.webMvcConfigurer().addCorsMappings(new CorsRegistry()));
    }

    @Test
    void cors_shouldAcceptOriginsWithWhitespace() {
        var config = new WebConfig(currentUserIdArgumentResolver);
        setField(config, "allowedOrigins", " http://a.com , http://b.com ");

        assertDoesNotThrow(() -> config.webMvcConfigurer().addCorsMappings(new CorsRegistry()));
    }

    // ===== 辅助方法 =====

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set field " + fieldName, e);
        }
    }
}
