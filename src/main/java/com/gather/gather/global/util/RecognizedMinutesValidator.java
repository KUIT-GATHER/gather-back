package com.gather.gather.global.util;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;

/** 봉사 인정시간(분 단위) 입력값 검증 — 개인 봉사 참여와 모임 봉사 참여가 동일한 규칙(10분 단위, 양수, 상한)을 공유한다. */
public final class RecognizedMinutesValidator {

    private static final int UNIT_MINUTES = 10;

    /** 1회 활동으로 인정 가능한 시간의 상한(30일치, 분 단위) — 1회만 입력 가능하고 수정 불가하므로 오입력이 영구히 남는 것을 막는다. */
    private static final int MAX_MINUTES = 30 * 24 * 60;

    private RecognizedMinutesValidator() {}

    public static void validate(Integer recognizedMinutes) {
        if (recognizedMinutes == null
                || recognizedMinutes <= 0
                || recognizedMinutes > MAX_MINUTES
                || recognizedMinutes % UNIT_MINUTES != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
