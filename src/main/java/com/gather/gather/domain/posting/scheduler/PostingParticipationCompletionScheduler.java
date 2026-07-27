package com.gather.gather.domain.posting.scheduler;

import com.gather.gather.domain.posting.service.PostingParticipationCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "posting.participation-completion",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PostingParticipationCompletionScheduler {

    private final PostingParticipationCompletionService postingParticipationCompletionService;

    /** 매일 새벽 4시 30분(KST) 1회 실행. 만료 공고 비활성화 배치(4시) 직후 돌려 최신 isActive 상태를 참고할 수 있게 한다. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void completeExpiredParticipations() {
        try {
            int count = postingParticipationCompletionService.completeExpiredParticipations();
            log.info("봉사 참여 완료 전이 완료. count={}", count);
        } catch (RuntimeException e) {
            log.error("봉사 참여 완료 전이 배치 실패", e);
        }
    }
}
