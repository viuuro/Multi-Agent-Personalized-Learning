package com.edu.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 学习成果评分专用线程池配置。
 *
 * AI 评价是一个耗时操作（通常需要 5-30 秒），如果同步执行会阻塞 HTTP 响应。
 * 通过 @EnableAsync + @Async 注解，将 AI 评价调用改为异步执行：
 *
 * ========== 为什么需要异步 ==========
 *   同步方式：用户 POST 提交 → 等 AI 评价完（5-30s）→ 返回 submissionId
 *   异步方式：用户 POST 提交 → 立即返回 submissionId → 后台异步 AI 评价
 *
 * ========== 使用场景 ==========
 *   SubmissionService.evaluateAsync() 方法标注了 @Async 注解，
 *   Spring 会自动在独立的线程池中执行该方法，不阻塞主线程。
 *
 * ========== 工作流程 ==========
 *   SubmissionController.submit()
 *     → SubmissionService.submit()    [主线程，同步]
 *       → submissionRepository.save() [保存提交，立即返回]
 *       → evaluateAsync()             [Spring 异步线程池执行]
 *         → callAiApi()              [HTTP 调用 AI，可能耗时]
 *         → evaluationRepository.save() [保存评价结果]
 *         → submission.setStatus(EVALUATED) [更新状态]
 *
 *   ========== 【Spring Boot】在本类的使用 ==========
 *   - @Configuration: 声明为配置类
 *   - 使用显式 Executor，避免同类内部调用导致 @Async 代理失效。
 */
@Configuration  // 【Spring Boot】配置类注解，启动时自动加载
public class AsyncConfig {

    @Bean(name = "submissionExecutor")
    public Executor submissionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("submission-eval-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
