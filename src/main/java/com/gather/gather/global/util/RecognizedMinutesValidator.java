package com.gather.gather.global.util;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;

/** 봉사 인정시간(분 단위) 입력값 검증 — 개인 봉사 참여와 모임 봉사 참여가 동일한 규칙(10분 단위, 양수)을 공유한다. */
public final class RecognizedMinutesValidator {

    private static final int UNIT_MINUTES = 10;

    private RecognizedMinutesValidator() {}

    public static void validate(Integer recognizedMinutes) {
        if (recognizedMinutes == null
                || recognizedMinutes <= 0
                || recognizedMinutes % UNIT_MINUTES != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
