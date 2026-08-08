package com.gather.gather.domain.posting.scheduler;

import com.gather.gather.domain.posting.service.PostingSyncResult;
import com.gather.gather.domain.posting.service.VmsPostingSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * robots.txt 미확인 상태이므로 {@code matchIfMissing = false}로 기본 비활성화한다(1365 스케줄러는 matchIfMissing=true).
 * 배포 전 robots.txt/이용약관 확인이 끝나야 활성화할 것.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "vms.crawl",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class VmsPostingSyncScheduler {

    private final VmsPostingSyncService vmsPostingSyncService;

    /** 매일 새벽 4시(KST) 1회 실행. 1365 동기화(새벽 3시)와 겹치지 않게 분리. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncPostings() {
        try {
            PostingSyncResult result = vmsPostingSyncService.syncRecentPostings();
            log.info("VMS 봉사공고 동기화 배치 성공. {}", result);
        } catch (RuntimeException e) {
            log.error("VMS 봉사공고 동기화 배치 전체 실패.", e);
        }
    }
}
