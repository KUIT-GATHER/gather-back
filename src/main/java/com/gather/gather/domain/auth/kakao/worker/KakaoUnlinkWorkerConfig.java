package com.gather.gather.domain.auth.kakao.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "kakao.admin",
        name = {"enabled", "unlink-worker.enabled"},
        havingValue = "true")
public class KakaoUnlinkWorkerConfig {

    public static final String TASK_SCHEDULER_BEAN_NAME = "kakaoUnlinkTaskScheduler";

    @Bean(TASK_SCHEDULER_BEAN_NAME)
    ThreadPoolTaskScheduler kakaoUnlinkTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("kakao-unlink-worker-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(120);
        return scheduler;
    }
}
