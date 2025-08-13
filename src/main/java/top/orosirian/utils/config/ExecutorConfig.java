package top.orosirian.utils.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ExecutorConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());       // 核心线程数：根据CPU核心数设定
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);    // 最大线程数
        executor.setQueueCapacity(200);     // 队列容量
        executor.setThreadNamePrefix("async-task-");    // 线程名前缀
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());    // 拒绝策略：由调用者线程处理
        executor.initialize();
        return executor;
    }

}
