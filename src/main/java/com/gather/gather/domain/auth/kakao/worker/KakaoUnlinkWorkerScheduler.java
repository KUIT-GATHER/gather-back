package com.gather.gather.domain.auth.kakao.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "kakao.admin",
        name = {"enabled", "unlink-worker.enabled"},
        havingValue = "true")
public class KakaoUnlinkWorkerScheduler {

    private final KakaoUnlinkWorker worker;

    @Scheduled(
            fixedDelayString = "${kakao.admin.unlink-worker.poll-interval:30s}",
            scheduler = KakaoUnlinkWorkerConfig.TASK_SCHEDULER_BEAN_NAME)
    public void poll() {
        try {
            worker.runBatch();
        } catch (RuntimeException exception) {
            log.error(
                    "Kakao unlink worker batch failed: failureType={}",
                    exception.getClass().getName(),
                    exception);
        }
    }
}
