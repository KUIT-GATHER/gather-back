package com.gather.gather.domain.user.controller;

/** 사용자 및 프로필 이미지 API의 Swagger 공통 오류 응답 예시. */
final class UserSwaggerExamples {

    private UserSwaggerExamples() {}

    static final String VALIDATION_ERROR =
            """
            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "요청 값이 올바르지 않습니다." } }
            """;
    static final String USER_NOT_FOUND =
            """
            { "success": false, "data": null, "error": { "code": "USER_NOT_FOUND", "message": "사용자를 찾을 수 없습니다." } }
            """;
    static final String UNSUPPORTED_PROFILE_IMAGE_TYPE =
            """
            { "success": false, "data": null, "error": { "code": "UNSUPPORTED_PROFILE_IMAGE_TYPE", "message": "지원하지 않는 프로필 이미지 형식입니다." } }
            """;
    static final String PROFILE_IMAGE_SIZE_EXCEEDED =
            """
            { "success": false, "data": null, "error": { "code": "PROFILE_IMAGE_SIZE_EXCEEDED", "message": "프로필 이미지의 허용 크기를 초과했습니다." } }
            """;
    static final String PROFILE_IMAGE_UPLOAD_LIMIT_EXCEEDED =
            """
            { "success": false, "data": null, "error": { "code": "PROFILE_IMAGE_UPLOAD_LIMIT_EXCEEDED", "message": "처리되지 않은 프로필 이미지 업로드 요청이 너무 많습니다." } }
            """;
    static final String INVALID_PROFILE_IMAGE_KEY =
            """
            { "success": false, "data": null, "error": { "code": "INVALID_PROFILE_IMAGE_KEY", "message": "올바르지 않은 프로필 이미지 경로입니다." } }
            """;
    static final String PROFILE_IMAGE_UPLOAD_EXPIRED =
            """
            { "success": false, "data": null, "error": { "code": "PROFILE_IMAGE_UPLOAD_EXPIRED", "message": "프로필 이미지 업로드 요청이 만료되었습니다." } }
            """;
    static final String PROFILE_IMAGE_SIZE_MISMATCH =
            """
            { "success": false, "data": null, "error": { "code": "PROFILE_IMAGE_SIZE_MISMATCH", "message": "요청한 크기와 업로드된 이미지 크기가 다릅니다." } }
            """;
    static final String INVALID_PROFILE_IMAGE_CONTENT =
            """
            { "success": false, "data": null, "error": { "code": "INVALID_PROFILE_IMAGE_CONTENT", "message": "실제 프로필 이미지 형식이 올바르지 않습니다." } }
            """;
    static final String PROFILE_IMAGE_OBJECT_NOT_FOUND =
            """
            { "success": false, "data": null, "error": { "code": "PROFILE_IMAGE_OBJECT_NOT_FOUND", "message": "업로드된 프로필 이미지 객체를 찾을 수 없습니다." } }
            """;
    static final String PROFILE_IMAGE_UPLOAD_CONFLICT =
            """
            { "success": false, "data": null, "error": { "code": "PROFILE_IMAGE_UPLOAD_CONFLICT", "message": "업로드된 프로필 이미지가 변경되어 다시 시도해야 합니다." } }
            """;
    static final String S3_OPERATION_FAILED =
            """
            { "success": false, "data": null, "error": { "code": "S3_OPERATION_FAILED", "message": "이미지 저장소 연동에 실패했습니다." } }
            """;
    static final String UNAUTHORIZED =
            """
            { "success": false, "data": null, "error": { "code": "UNAUTHORIZED", "message": "인증이 필요합니다." } }
            """;
    static final String INVALID_TOKEN =
            """
            { "success": false, "data": null, "error": { "code": "INVALID_TOKEN", "message": "유효하지 않은 토큰입니다." } }
            """;
    static final String EXPIRED_TOKEN =
            """
            { "success": false, "data": null, "error": { "code": "EXPIRED_TOKEN", "message": "만료된 토큰입니다." } }
            """;
    static final String WITHDRAWAL_PENDING_USER =
            """
            { "success": false, "data": null, "error": { "code": "WITHDRAWAL_PENDING_USER", "message": "탈퇴 처리 중인 계정입니다." } }
            """;
    static final String WITHDRAWN_USER =
            """
            { "success": false, "data": null, "error": { "code": "WITHDRAWN_USER", "message": "탈퇴한 계정입니다." } }
            """;
    static final String INVALID_INTEREST_CATEGORY_COUNT =
            """
            { "success": false, "data": null, "error": { "code": "INVALID_INTEREST_CATEGORY_COUNT", "message": "관심 카테고리는 중복 없이 1개 이상 선택해야 합니다." } }
            """;
    static final String REGION_NOT_FOUND =
            """
            { "success": false, "data": null, "error": { "code": "REGION_NOT_FOUND", "message": "활동 지역을 찾을 수 없습니다." } }
            """;
    static final String DUPLICATE_NICKNAME =
            """
            { "success": false, "data": null, "error": { "code": "DUPLICATE_NICKNAME", "message": "이미 사용 중인 닉네임입니다." } }
            """;
    static final String ACCOUNT_TERMINATION_COMPLETED =
            """
            { "success": true, "data": { "status": "COMPLETED", "occurredAt": "2026-08-01T14:00:00Z" }, "error": null }
            """;
    static final String ACCOUNT_TERMINATION_ACCEPTED =
            """
            { "success": true, "data": { "status": "ACCEPTED", "occurredAt": "2026-08-01T14:00:00Z" }, "error": null }
            """;
    static final String ACCOUNT_TERMINATION_STATE_CONFLICT =
            """
            { "success": false, "data": null, "error": { "code": "ACCOUNT_TERMINATION_STATE_CONFLICT", "message": "계정 탈퇴 상태가 올바르지 않습니다." } }
            """;
    static final String PASSWORD_CHANGED =
            """
            { "success": true, "data": null, "error": null }
            """;
    static final String CURRENT_PASSWORD_MISMATCH =
            """
            { "success": false, "data": null, "error": { "code": "CURRENT_PASSWORD_MISMATCH", "message": "현재 비밀번호가 올바르지 않습니다." } }
            """;
    static final String PASSWORD_MISMATCH =
            """
            { "success": false, "data": null, "error": { "code": "PASSWORD_MISMATCH", "message": "비밀번호가 일치하지 않습니다." } }
            """;
    static final String PASSWORD_CHANGE_NOT_AVAILABLE =
            """
            { "success": false, "data": null, "error": { "code": "PASSWORD_CHANGE_NOT_AVAILABLE", "message": "비밀번호 변경을 지원하지 않는 계정입니다." } }
            """;
    static final String SUSPENDED_USER =
            """
            { "success": false, "data": null, "error": { "code": "SUSPENDED_USER", "message": "이용 정지된 계정입니다." } }
            """;
    static final String INTERNAL_SERVER_ERROR =
            """
            { "success": false, "data": null, "error": { "code": "INTERNAL_SERVER_ERROR", "message": "서버 오류가 발생했습니다." } }
            """;
}
