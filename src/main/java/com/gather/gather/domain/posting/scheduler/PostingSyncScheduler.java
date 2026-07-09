package com.gather.gather.domain.posting.scheduler;

import com.gather.gather.domain.posting.service.PostingSyncResult;
import com.gather.gather.domain.posting.service.PostingSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "posting.sync",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PostingSyncScheduler {

    private final PostingSyncService postingSyncService;

    /** 매일 새벽 3시(KST) 1회 실행. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncPostings() {
        try {
            PostingSyncResult result = postingSyncService.syncRecentPostings();
            log.info("봉사공고 동기화 배치 성공. {}", result);
        } catch (RuntimeException e) {
            log.error(
                    "봉사공고 동기화 배치 전체 실패. 오늘 마감되는 공고는 API에서 다음날부터 완전히 사라져 재수집이 불가능하므로,"
                            + " 이번 실패로 인한 데이터 유실 여부를 수동으로 확인해야 합니다.",
                    e);
        }
    }
}
