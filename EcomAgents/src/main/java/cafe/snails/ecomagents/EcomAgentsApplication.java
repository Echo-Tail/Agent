package cafe.snails.ecomagents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * 企业电商智能体管理平台启动入口。
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class EcomAgentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomAgentsApplication.class, args);
    }

}
