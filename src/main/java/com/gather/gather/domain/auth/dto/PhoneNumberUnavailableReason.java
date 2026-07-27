package com.gather.gather.domain.auth.dto;

/** 전화번호를 쓸 수 없는 이유. 사용 가능하면 응답에서 null이다. */
public enum PhoneNumberUnavailableReason {
    /** 사용 중인 계정이 점유하고 있어 영구히 쓸 수 없다. */
    IN_USE,

    /** 탈퇴한 계정이 점유 중이라 유예가 끝나면 다시 쓸 수 있다. */
    WITHDRAWN_COOLDOWN
}
