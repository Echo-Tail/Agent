package cafe.snails.ecomagents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动上下文加载测试。
 */
class EcomAgentsApplicationTests {

    @Test
    void applicationClassShouldBeSpringBootApplication() {
        assert EcomAgentsApplication.class.isAnnotationPresent(SpringBootApplication.class);
    }

}
