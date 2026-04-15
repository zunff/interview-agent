package com.zunff.interview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 虚拟线程池配置
 * 使用 Java 21 虚拟线程 (Virtual Threads) 提供高性能异步执行能力
 *
 * 特点：
 * - 轻量级：可以创建数百万个虚拟线程而不耗尽资源
 * - 自动调度：由 JVM 调度，无需手动管理线程池大小
 * - 低开销：虚拟线程创建和销毁成本极低
 */
@Slf4j
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * 虚拟线程 ExecutorService
     * 适用于：CPU 密集型任务、IO 密集型任务、高并发场景
     *
     * 使用示例：
     * <pre>
     * &#64;Autowired
     * private ExecutorService virtualThreadExecutor;
     *
     * virtualThreadExecutor.submit(() -> {
     *     // 异步任务逻辑
     * });
     * </pre>
     */
    @Bean(name = "virtualThreadExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        log.info("初始化虚拟线程池 ExecutorService");
        ThreadFactory factory = Thread.ofVirtual()
                .name("virtual-thread-", 0)
                .uncaughtExceptionHandler((thread, throwable) ->
                        log.error("虚拟线程异常: {}", thread.getName(), throwable))
                .factory();

        return Executors.newThreadPerTaskExecutor(factory);
    }

}
