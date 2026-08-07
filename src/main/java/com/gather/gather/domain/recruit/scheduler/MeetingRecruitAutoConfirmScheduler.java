package com.gather.gather.domain.recruit.scheduler;

import com.gather.gather.domain.recruit.service.MeetingRecruitManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 신청 마감 시각이 지난 UNCONFIRMED 모집공고를 주기적으로 자동 확정한다(#13). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "gather.recruit.auto-confirm",
        name = "scheduler-enabled",
        havingValue = "true")
public class MeetingRecruitAutoConfirmScheduler {

    private final MeetingRecruitManagementService meetingRecruitManagementService;

    @Scheduled(cron = "${gather.recruit.auto-confirm.cron}", zone = "Asia/Seoul")
    public void autoConfirm() {
        log.info("모집공고 자동 확정 스케줄러를 시작합니다.");
        int confirmedCount = meetingRecruitManagementService.autoConfirmExpiredRecruits();
        log.info("모집공고 자동 확정 스케줄러를 완료했습니다. confirmedCount={}", confirmedCount);
    }
}
