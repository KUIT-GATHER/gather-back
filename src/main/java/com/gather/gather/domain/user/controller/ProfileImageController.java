package com.gather.gather.domain.user.controller;

import com.gather.gather.domain.user.dto.ProfileImageCurrentResponse;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlRequest;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlResponse;
import com.gather.gather.domain.user.dto.ProfileImageUpdateRequest;
import com.gather.gather.domain.user.dto.ProfileImageUpdateResponse;
import com.gather.gather.domain.user.service.ProfileImageService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Profile Image", description = "사용자 프로필 이미지 업로드·변경 API")
@RestController
@RequestMapping("/api/v1/users/me/profile-image")
@RequiredArgsConstructor
public class ProfileImageController {

    private static final String JSON = "application/json";

    private final ProfileImageService profileImageService;

    @Operation(summary = "현재 프로필 이미지 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "현재 프로필 이미지 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "UNAUTHORIZED",
                                            value = UserSwaggerExamples.UNAUTHORIZED),
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = UserSwaggerExamples.INVALID_TOKEN),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = UserSwaggerExamples.EXPIRED_TOKEN),
                                    @ExampleObject(
                                            name = "REVOKED_TOKEN",
                                            value = UserSwaggerExamples.REVOKED_TOKEN)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "USER_NOT_FOUND",
                                                value = UserSwaggerExamples.USER_NOT_FOUND)))
    })
    @GetMapping
    public ApiResponse<ProfileImageCurrentResponse> getCurrentProfileImage() {
        return ApiResponse.success(profileImageService.getCurrentProfileImage());
    }

    @Operation(
            summary = "프로필 이미지 Presigned PUT URL 발급",
            description =
                    """
                    JPEG, PNG, WebP 형식의 최대 5MB 이미지를 업로드할 URL을 발급합니다.
                    프론트는 uploadUrl로 PUT 요청을 보내며, 발급 요청과 동일한 Content-Type과
                    If-None-Match: * 헤더를 사용해야 합니다. S3가 412 Precondition Failed를 반환하면
                    기존 URL을 재시도하지 말고 새 Presigned URL을 발급받아야 합니다.
                    업로드 성공 후 응답의 objectKey로 프로필 이미지 반영 API를 호출합니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Presigned PUT URL 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값, 이미지 형식 또는 허용 크기 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value = UserSwaggerExamples.VALIDATION_ERROR),
                                    @ExampleObject(
                                            name = "UNSUPPORTED_PROFILE_IMAGE_TYPE",
                                            value =
                                                    UserSwaggerExamples
                                                            .UNSUPPORTED_PROFILE_IMAGE_TYPE),
                                    @ExampleObject(
                                            name = "PROFILE_IMAGE_SIZE_EXCEEDED",
                                            value = UserSwaggerExamples.PROFILE_IMAGE_SIZE_EXCEEDED)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "UNAUTHORIZED",
                                            value = UserSwaggerExamples.UNAUTHORIZED),
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = UserSwaggerExamples.INVALID_TOKEN),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = UserSwaggerExamples.EXPIRED_TOKEN),
                                    @ExampleObject(
                                            name = "REVOKED_TOKEN",
                                            value = UserSwaggerExamples.REVOKED_TOKEN)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "USER_NOT_FOUND",
                                                value = UserSwaggerExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "미반영 업로드 발급 횟수 초과",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PROFILE_IMAGE_UPLOAD_LIMIT_EXCEEDED",
                                                value =
                                                        UserSwaggerExamples
                                                                .PROFILE_IMAGE_UPLOAD_LIMIT_EXCEEDED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "502",
                description = "S3 연동 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "S3_OPERATION_FAILED",
                                                value = UserSwaggerExamples.S3_OPERATION_FAILED)))
    })
    @PostMapping("/presigned-url")
    public ApiResponse<ProfileImagePresignedUrlResponse> createPresignedUrl(
            @Valid @RequestBody ProfileImagePresignedUrlRequest request) {
        return ApiResponse.success(profileImageService.createPresignedUrl(request));
    }

    @Operation(
            summary = "업로드된 프로필 이미지 반영",
            description =
                    """
                    Presigned PUT 업로드가 끝난 객체를 검증한 뒤 현재 사용자의 프로필 이미지로 반영합니다.
                    profileImageUrl은 버킷 정책으로 공개 조회 가능한 URL입니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "프로필 이미지 반영 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "잘못된 key, 만료된 업로드, 크기 또는 이미지 형식 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value = UserSwaggerExamples.VALIDATION_ERROR),
                                    @ExampleObject(
                                            name = "INVALID_PROFILE_IMAGE_KEY",
                                            value = UserSwaggerExamples.INVALID_PROFILE_IMAGE_KEY),
                                    @ExampleObject(
                                            name = "PROFILE_IMAGE_UPLOAD_EXPIRED",
                                            value =
                                                    UserSwaggerExamples
                                                            .PROFILE_IMAGE_UPLOAD_EXPIRED),
                                    @ExampleObject(
                                            name = "PROFILE_IMAGE_SIZE_EXCEEDED",
                                            value =
                                                    UserSwaggerExamples
                                                            .PROFILE_IMAGE_SIZE_EXCEEDED),
                                    @ExampleObject(
                                            name = "PROFILE_IMAGE_SIZE_MISMATCH",
                                            value =
                                                    UserSwaggerExamples
                                                            .PROFILE_IMAGE_SIZE_MISMATCH),
                                    @ExampleObject(
                                            name = "INVALID_PROFILE_IMAGE_CONTENT",
                                            value =
                                                    UserSwaggerExamples
                                                            .INVALID_PROFILE_IMAGE_CONTENT)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "UNAUTHORIZED",
                                            value = UserSwaggerExamples.UNAUTHORIZED),
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = UserSwaggerExamples.INVALID_TOKEN),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = UserSwaggerExamples.EXPIRED_TOKEN),
                                    @ExampleObject(
                                            name = "REVOKED_TOKEN",
                                            value = UserSwaggerExamples.REVOKED_TOKEN)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "사용자 또는 업로드 객체를 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "PROFILE_IMAGE_OBJECT_NOT_FOUND",
                                            value =
                                                    UserSwaggerExamples
                                                            .PROFILE_IMAGE_OBJECT_NOT_FOUND),
                                    @ExampleObject(
                                            name = "USER_NOT_FOUND",
                                            value = UserSwaggerExamples.USER_NOT_FOUND)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "검증 이후 업로드 객체가 변경됨",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PROFILE_IMAGE_UPLOAD_CONFLICT",
                                                value =
                                                        UserSwaggerExamples
                                                                .PROFILE_IMAGE_UPLOAD_CONFLICT))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "502",
                description = "S3 연동 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "S3_OPERATION_FAILED",
                                                value = UserSwaggerExamples.S3_OPERATION_FAILED)))
    })
    @PatchMapping
    public ApiResponse<ProfileImageUpdateResponse> updateProfileImage(
            @Valid @RequestBody ProfileImageUpdateRequest request) {
        return ApiResponse.success(profileImageService.updateProfileImage(request));
    }
}
