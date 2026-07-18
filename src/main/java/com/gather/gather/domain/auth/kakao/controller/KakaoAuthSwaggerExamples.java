package com.gather.gather.domain.auth.kakao.controller;

/**
 * KakaoAuthController의 Swagger 응답 예시 JSON 상수 모음. code/message는 ErrorCode 정의와 일치시킨다. annotation
 * value로 사용할 수 있도록 모든 값은 compile-time constant(텍스트 블록)로 선언한다.
 */
final class KakaoAuthSwaggerExamples {

    private KakaoAuthSwaggerExamples() {}

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
    static final String INTERNAL_SERVER_ERROR_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "INTERNAL_SERVER_ERROR",
                "message": "서버 오류가 발생했습니다."
              }
            }
            """;
    static final String SIGNUP_TOKEN_EXPIRED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "SIGNUP_TOKEN_EXPIRED",
                "message": "가입 인증이 만료되었습니다. 카카오 로그인부터 다시 진행해주세요."
              }
            }
            """;
    static final String SIGNUP_TOKEN_INVALID_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "SIGNUP_TOKEN_INVALID",
                "message": "유효하지 않은 가입 인증입니다. 카카오 로그인부터 다시 진행해주세요."
              }
            }
            """;
    static final String ALREADY_REGISTERED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "ALREADY_REGISTERED",
                "message": "이미 가입된 계정입니다."
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
    static final String INVALID_ACTIVITY_REGION_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "INVALID_ACTIVITY_REGION",
                "message": "활동 지역은 시군구 단위로 1개 선택해야 합니다."
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
}
