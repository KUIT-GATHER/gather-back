package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;

/**
 * 참여 일정(participationStartDate ~ participationEndDate) 서버 검증.
 *
 * <p>이번 정책에서는 {@code Posting.actWkdy}를 신청 날짜 검증에 사용하지 않는다 — 1365는 raw string을 그대로 저장하고 VMS 동기화에는 대응
 * 필드 자체가 없어 소스 간 정합성이 부족하기 때문이다(devplan 정책 결정 참고). 검증은 공고의 전체 활동기간(actStartDate ~ actEndDate, 없으면
 * activityDate로 대체)과 오늘 날짜만 기준으로 한다.
 */
final class PostingParticipationDateValidator {

    private PostingParticipationDateValidator() {}

    static void validate(Posting posting, LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DATE_INVALID_RANGE);
        }
        if (startDate.isBefore(today)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DATE_IN_PAST);
        }

        LocalDate postingStart =
                posting.getActStartDate() != null
                        ? posting.getActStartDate()
                        : posting.getActivityDate();
        LocalDate postingEnd =
                posting.getActEndDate() != null
                        ? posting.getActEndDate()
                        : posting.getActivityDate();

        if (postingStart == null || postingEnd == null) {
            // 활동기간 정보 자체가 없는 공고(정합성 예외 케이스) — 참여 일정을 검증할 기준이 없어 등록을 막는다.
            throw new BusinessException(ErrorCode.PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD);
        }
        if (startDate.isBefore(postingStart) || endDate.isAfter(postingEnd)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD);
        }
    }
}
