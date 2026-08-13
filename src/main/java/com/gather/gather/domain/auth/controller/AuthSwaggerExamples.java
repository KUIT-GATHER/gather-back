package com.gather.gather.domain.auth.controller;

/**
 * AuthController의 Swagger 응답 예시 JSON 상수 모음. 공통 실패 응답(success/data/error 엔벌롭)의 code/message는
 * ErrorCode 정의와 일치시킨다. annotation value로 사용할 수 있도록 모든 값은 compile-time constant(텍스트 블록)로 선언한다.
 */
final class AuthSwaggerExamples {

    private AuthSwaggerExamples() {}

    static final String VALIDATION_ERROR_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "요청 값이 올바르지 않습니다."
              }
            }
            """;
    static final String DUPLICATE_EMAIL_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "DUPLICATE_EMAIL",
                "message": "이미 사용 중인 이메일입니다."
              }
            }
            """;
    static final String DUPLICATE_PHONE_NUMBER_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "DUPLICATE_PHONE_NUMBER",
                "message": "이미 사용 중인 전화번호입니다."
              }
            }
            """;
    static final String DUPLICATE_NICKNAME_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "DUPLICATE_NICKNAME",
                "message": "이미 사용 중인 닉네임입니다."
              }
            }
            """;
    static final String EMAIL_SEND_FAILED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EMAIL_SEND_FAILED",
                "message": "이메일 발송에 실패했습니다."
              }
            }
            """;
    static final String INVALID_VERIFICATION_CODE_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "INVALID_VERIFICATION_CODE",
                "message": "인증 코드가 올바르지 않습니다."
              }
            }
            """;
    static final String EXPIRED_VERIFICATION_CODE_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EXPIRED_VERIFICATION_CODE",
                "message": "인증 코드가 만료되었습니다."
              }
            }
            """;
    static final String EMAIL_VERIFICATION_NOT_FOUND_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EMAIL_VERIFICATION_NOT_FOUND",
                "message": "이메일 인증 요청을 찾을 수 없습니다."
              }
            }
            """;
    static final String EMAIL_RESEND_TOO_SOON_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EMAIL_RESEND_TOO_SOON",
                "message": "인증 코드를 방금 발송했습니다. 잠시 후 다시 시도해주세요."
              }
            }
            """;
    static final String EMAIL_SEND_LIMIT_EXCEEDED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EMAIL_SEND_LIMIT_EXCEEDED",
                "message": "하루 이메일 인증 발송 횟수를 초과했습니다. 내일 다시 시도해주세요."
              }
            }
            """;
    static final String EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED",
                "message": "인증 코드 입력 가능 횟수를 초과했습니다. 코드를 다시 발송해주세요."
              }
            }
            """;
    static final String PHONE_VERIFICATION_NOT_FOUND_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PHONE_VERIFICATION_NOT_FOUND",
                "message": "휴대폰 인증 요청을 찾을 수 없습니다."
              }
            }
            """;
    static final String PHONE_VERIFICATION_EXPIRED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PHONE_VERIFICATION_EXPIRED",
                "message": "휴대폰 인증 요청이 만료되었습니다."
              }
            }
            """;
    static final String PHONE_VERIFICATION_REQUIRED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PHONE_VERIFICATION_REQUIRED",
                "message": "휴대폰 인증이 필요합니다."
              }
            }
            """;
    static final String PHONE_VERIFICATION_PROVIDER_UNAVAILABLE_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PHONE_VERIFICATION_PROVIDER_UNAVAILABLE",
                "message": "휴대폰 인증 서비스를 일시적으로 사용할 수 없습니다."
              }
            }
            """;
    static final String PHONE_VERIFICATION_RATE_LIMITED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PHONE_VERIFICATION_RATE_LIMITED",
                "message": "휴대폰 인증 요청이 많습니다. 잠시 후 다시 시도해주세요."
              }
            }
            """;
    static final String PASSWORD_MISMATCH_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PASSWORD_MISMATCH",
                "message": "비밀번호가 일치하지 않습니다."
              }
            }
            """;
    static final String EMAIL_NOT_VERIFIED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EMAIL_NOT_VERIFIED",
                "message": "이메일 인증이 완료되지 않았습니다."
              }
            }
            """;
    static final String REQUIRED_TERMS_NOT_AGREED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "REQUIRED_TERMS_NOT_AGREED",
                "message": "필수 약관 동의가 필요합니다."
              }
            }
            """;
    static final String INVALID_INTEREST_CATEGORY_COUNT_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "INVALID_INTEREST_CATEGORY_COUNT",
                "message": "관심 카테고리는 중복 없이 1개 이상 선택해야 합니다."
              }
            }
            """;
    static final String REGION_NOT_FOUND_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "REGION_NOT_FOUND",
                "message": "활동 지역을 찾을 수 없습니다."
              }
            }
            """;
    static final String INVALID_LOGIN_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "INVALID_LOGIN",
                "message": "이메일 또는 비밀번호가 올바르지 않습니다."
              }
            }
            """;
    static final String SUSPENDED_USER_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "SUSPENDED_USER",
                "message": "이용 정지된 계정입니다."
              }
            }
            """;
    static final String WITHDRAWAL_PENDING_USER_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WITHDRAWAL_PENDING_USER",
                "message": "탈퇴 처리 중인 계정입니다."
              }
            }
            """;
    static final String WITHDRAWN_USER_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WITHDRAWN_USER",
                "message": "탈퇴한 계정입니다."
              }
            }
            """;
    static final String ACCOUNT_REJOIN_BLOCKED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "ACCOUNT_REJOIN_BLOCKED",
                "message": "탈퇴 후 7일 동안 재가입할 수 없습니다."
              }
            }
            """;
    static final String INVALID_TOKEN_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "INVALID_TOKEN",
                "message": "유효하지 않은 토큰입니다."
              }
            }
            """;
    static final String EXPIRED_TOKEN_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "EXPIRED_TOKEN",
                "message": "만료된 토큰입니다."
              }
            }
            """;
    static final String REVOKED_TOKEN_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "REVOKED_TOKEN",
                "message": "폐기된 토큰입니다."
              }
            }
            """;
}
