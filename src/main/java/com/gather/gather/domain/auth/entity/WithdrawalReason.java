package com.gather.gather.domain.auth.entity;

/** 탈퇴 경로. KAKAO_UNLINK는 사용자가 카카오에서 연결을 끊어 본인도 모르게 탈퇴된 경우라, 나중에 재가입 유예를 경로별로 다르게 가져갈 여지를 남긴다. */
public enum WithdrawalReason {
    SELF,
    KAKAO_UNLINK
}
