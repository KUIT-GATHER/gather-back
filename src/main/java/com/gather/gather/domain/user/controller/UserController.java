package com.gather.gather.domain.user.controller;

import com.gather.gather.domain.auth.service.RefreshTokenCookieProvider;
import com.gather.gather.domain.auth.service.UserWithdrawalService;
import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.dto.UserProfileUpdateRequest;
import com.gather.gather.domain.user.service.UserProfileService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "마이페이지 프로필 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserController {

    private static final String JSON = "application/json";

    private final UserProfileService userProfileService;
    private final UserWithdrawalService userWithdrawalService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 마이페이지 프로필을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "내 프로필 조회 성공"),
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
    public ApiResponse<UserProfileResponse> getMyProfile() {
        return ApiResponse.success(userProfileService.getMyProfile());
    }

    @Operation(
            summary = "내 프로필 수정",
            description = "회원가입과 동일한 필드 집합을 수정합니다. 프로필 사진은 이 API의 대상이 아니며 별도의 프로필 사진 API로 관리합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "내 프로필 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값, 활동 지역 또는 관심 카테고리 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value = UserSwaggerExamples.VALIDATION_ERROR),
                                    @ExampleObject(
                                            name = "INVALID_ACTIVITY_REGION",
                                            value = UserSwaggerExamples.INVALID_ACTIVITY_REGION),
                                    @ExampleObject(
                                            name = "INVALID_INTEREST_CATEGORY_COUNT",
                                            value =
                                                    UserSwaggerExamples
                                                            .INVALID_INTEREST_CATEGORY_COUNT)
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
                description = "사용자 또는 활동 지역을 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "USER_NOT_FOUND",
                                            value = UserSwaggerExamples.USER_NOT_FOUND),
                                    @ExampleObject(
                                            name = "REGION_NOT_FOUND",
                                            value = UserSwaggerExamples.REGION_NOT_FOUND)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 사용 중인 닉네임",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "DUPLICATE_NICKNAME",
                                                value = UserSwaggerExamples.DUPLICATE_NICKNAME)))
    })
    @PatchMapping
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(userProfileService.updateMyProfile(request));
    }

    @Operation(
            summary = "회원 탈퇴",
            description =
                    "로그인한 사용자를 탈퇴 처리하고 Refresh Token 쿠키를 만료시킵니다. "
                            + "서버가 Access Token을 무효화할 수 없으므로 클라이언트가 보관 중인 Access Token을 즉시 폐기해야 합니다. "
                            + "이미 탈퇴한 계정이 다시 호출해도 200입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "회원 탈퇴 성공"),
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
    @DeleteMapping
    public ApiResponse<Void> withdrawMyAccount(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.clear().toString());
        userWithdrawalService.withdrawMyAccount();
        return ApiResponse.success(null);
    }
}
