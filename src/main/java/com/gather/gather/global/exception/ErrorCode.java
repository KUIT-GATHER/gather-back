package com.gather.gather.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    EXPIRED_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    EMAIL_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "이메일 인증 요청을 찾을 수 없습니다."),
    EMAIL_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증 코드를 방금 발송했습니다. 잠시 후 다시 시도해주세요."),
    EMAIL_SEND_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, "하루 이메일 인증 발송 횟수를 초과했습니다. 내일 다시 시도해주세요."),
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, "인증 코드 입력 가능 횟수를 초과했습니다. 코드를 다시 발송해주세요."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관 동의가 필요합니다."),
    INVALID_ACTIVITY_REGION(HttpStatus.BAD_REQUEST, "활동 지역은 시군구 단위로 1개 선택해야 합니다."),
    INVALID_INTEREST_CATEGORY_COUNT(HttpStatus.BAD_REQUEST, "관심 카테고리는 중복 없이 1개 이상 선택해야 합니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "활동 지역을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "관심 카테고리를 찾을 수 없습니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    SUSPENDED_USER(HttpStatus.FORBIDDEN, "이용 정지된 계정입니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REVOKED_TOKEN(HttpStatus.UNAUTHORIZED, "폐기된 토큰입니다."),
    // 소셜 가입용 임시 토큰 전용. 위 access/refresh 토큰 코드와 구분하기 위해 SIGNUP_TOKEN_ 접두사를 쓴다.
    SIGNUP_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "가입 인증이 만료되었습니다. 카카오 로그인부터 다시 진행해주세요."),
    SIGNUP_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 가입 인증입니다. 카카오 로그인부터 다시 진행해주세요."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 계정입니다."),
    KAKAO_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인 서비스를 일시적으로 사용할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    MEETING_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 모임입니다."),
    MEETING_FULL(HttpStatus.CONFLICT, "모임 인원이 가득 찼습니다."),
    MEETING_CLOSED(HttpStatus.CONFLICT, "마감된 모임입니다."),
    INVALID_MEETING_TIME(HttpStatus.BAD_REQUEST, "모임 시간이 올바르지 않습니다."),

    POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "봉사공고를 찾을 수 없습니다."),
    BOOKMARK_DUPLICATE(HttpStatus.CONFLICT, "이미 북마크한 공고입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다."),

    MEETING_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "모임에 가입해야 이용할 수 있습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "게시글에 대한 권한이 없습니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "가입하지 않은 모임의 게시글은 열람할 수 없습니다."),
    NOTICE_HOST_ONLY(HttpStatus.FORBIDDEN, "공지는 모임장만 작성할 수 있습니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
