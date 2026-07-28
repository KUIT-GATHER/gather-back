package com.gather.gather.domain.auth.kakao.client;

/**
 * 카카오 연결 해제 호출 결과.
 *
 * <p>탈퇴는 이 호출 전에 이미 커밋되므로 실패를 예외로 올려 봐야 되돌릴 것이 없다. 대신 재시도할 가치가 있는지를 호출자가 판단할 수 있게 분류해서 돌려준다.
 */
public enum KakaoUnlinkResult {
    SUCCESS,

    /** 재시도해도 결과가 달라지지 않는다. social_account row를 정리한다. */
    ALREADY_UNLINKED,

    NOT_LINKED,

    /** 카카오 장애·요청 제한·네트워크 오류. row를 남겨 스케줄러가 다시 시도한다. */
    RETRYABLE_FAILURE
}
