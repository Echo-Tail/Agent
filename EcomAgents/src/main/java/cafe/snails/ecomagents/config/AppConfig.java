package cafe.snails.ecomagents.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.Executor;

/**
 * 应用通用配置：WebClient 和 LLM 流式响应的异步执行器。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {

    /** WebClient 构建器，用于 HTTP 请求 */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * LLM 流式对话的异步任务执行器。
     * 核心线程 2，最大线程 5，队列容量 50，线程名前缀 "llm-stream-"。
     */
    @Bean(name = "llmTaskExecutor")
    public Executor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("llm-stream-");
        executor.initialize();
        return executor;
    }
}
