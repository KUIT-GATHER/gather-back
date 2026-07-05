package com.gather.gather.domain.auth.controller;

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.EmailVerificationSendRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationSendResponse;
import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.LogoutRequest;
import com.gather.gather.domain.auth.dto.PhoneNumberAvailabilityRequest;
import com.gather.gather.domain.auth.dto.PhoneNumberAvailabilityResponse;
import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.dto.SignupResponse;
import com.gather.gather.domain.auth.dto.TokenReissueRequest;
import com.gather.gather.domain.auth.dto.TokenResponse;
import com.gather.gather.domain.auth.service.AuthService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증, 회원가입, 토큰 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String JSON = "application/json";

    private final AuthService authService;

    @Operation(summary = "이메일 인증 코드 발송", description = "회원가입에 사용할 이메일로 인증 코드를 발송합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "이메일 인증 코드 발송 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": {
                                                            "email": "test@example.com",
                                                            "expiresAt": "2026-06-28T12:10:00",
                                                            "message": "인증 코드가 발송되었습니다."
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "VALIDATION_ERROR",
                                                value =
                                                        AuthSwaggerExamples
                                                                .VALIDATION_ERROR_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 사용 중인 이메일",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "DUPLICATE_EMAIL",
                                                value =
                                                        AuthSwaggerExamples
                                                                .DUPLICATE_EMAIL_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "이메일 발송 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "EMAIL_SEND_FAILED",
                                                value =
                                                        AuthSwaggerExamples
                                                                .EMAIL_SEND_FAILED_EXAMPLE)))
    })
    @PostMapping("/email-verifications")
    public ApiResponse<EmailVerificationSendResponse> sendEmailVerificationCode(
            @RequestBody @Valid EmailVerificationSendRequest request) {
        return ApiResponse.success(authService.sendEmailVerificationCode(request));
    }

    @Operation(summary = "이메일 인증 코드 확인", description = "사용자가 입력한 이메일 인증 코드가 유효한지 확인합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "이메일 인증 코드 확인 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": {
                                                            "email": "test@example.com",
                                                            "verified": true,
                                                            "verifiedAt": "2026-06-28T12:05:00"
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패 또는 인증 코드 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value = AuthSwaggerExamples.VALIDATION_ERROR_EXAMPLE),
                                    @ExampleObject(
                                            name = "INVALID_VERIFICATION_CODE",
                                            value =
                                                    AuthSwaggerExamples
                                                            .INVALID_VERIFICATION_CODE_EXAMPLE),
                                    @ExampleObject(
                                            name = "EXPIRED_VERIFICATION_CODE",
                                            value =
                                                    AuthSwaggerExamples
                                                            .EXPIRED_VERIFICATION_CODE_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "이메일 인증 요청 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "EMAIL_VERIFICATION_NOT_FOUND",
                                                value =
                                                        AuthSwaggerExamples
                                                                .EMAIL_VERIFICATION_NOT_FOUND_EXAMPLE)))
    })
    @PostMapping("/email-verifications/confirm")
    public ApiResponse<EmailVerificationConfirmResponse> confirmEmailVerificationCode(
            @RequestBody @Valid EmailVerificationConfirmRequest request) {
        return ApiResponse.success(authService.confirmEmailVerificationCode(request));
    }

    @Operation(
            summary = "전화번호 중복 확인",
            description = "회원가입 기본 정보 단계에서 입력한 전화번호가 이미 가입에 사용되었는지 확인합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "전화번호 중복 확인 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "available",
                                            summary = "사용 가능한 전화번호",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "phoneNumber": "01012345678",
                                                        "available": true
                                                      },
                                                      "error": null
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "unavailable",
                                            summary = "이미 사용 중인 전화번호",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "phoneNumber": "01012345678",
                                                        "available": false
                                                      },
                                                      "error": null
                                                    }
                                                    """)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "VALIDATION_ERROR",
                                                value =
                                                        AuthSwaggerExamples
                                                                .VALIDATION_ERROR_EXAMPLE)))
    })
    @PostMapping("/phone-numbers/availability")
    public ApiResponse<PhoneNumberAvailabilityResponse> checkPhoneNumberAvailability(
            @RequestBody @Valid PhoneNumberAvailabilityRequest request) {
        return ApiResponse.success(authService.checkPhoneNumberAvailability(request));
    }

    @Operation(summary = "회원가입", description = "회원가입 성공 시 토큰은 발급하지 않습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "회원가입 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": {
                                                            "userId": 1,
                                                            "email": "test@example.com",
                                                            "name": "홍길동",
                                                            "nickname": "길동"
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패 또는 비즈니스 규칙 위반",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value = AuthSwaggerExamples.VALIDATION_ERROR_EXAMPLE),
                                    @ExampleObject(
                                            name = "PASSWORD_MISMATCH",
                                            value = AuthSwaggerExamples.PASSWORD_MISMATCH_EXAMPLE),
                                    @ExampleObject(
                                            name = "EMAIL_NOT_VERIFIED",
                                            value = AuthSwaggerExamples.EMAIL_NOT_VERIFIED_EXAMPLE),
                                    @ExampleObject(
                                            name = "REQUIRED_TERMS_NOT_AGREED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .REQUIRED_TERMS_NOT_AGREED_EXAMPLE),
                                    @ExampleObject(
                                            name = "INVALID_ACTIVITY_REGION_COUNT",
                                            value =
                                                    AuthSwaggerExamples
                                                            .INVALID_ACTIVITY_REGION_COUNT_EXAMPLE),
                                    @ExampleObject(
                                            name = "INVALID_INTEREST_CATEGORY_COUNT",
                                            value =
                                                    AuthSwaggerExamples
                                                            .INVALID_INTEREST_CATEGORY_COUNT_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "활동 지역 또는 관심 카테고리 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "REGION_NOT_FOUND",
                                            value = AuthSwaggerExamples.REGION_NOT_FOUND_EXAMPLE),
                                    @ExampleObject(
                                            name = "CATEGORY_NOT_FOUND",
                                            value = AuthSwaggerExamples.CATEGORY_NOT_FOUND_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "중복 데이터 존재",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "DUPLICATE_EMAIL",
                                            value = AuthSwaggerExamples.DUPLICATE_EMAIL_EXAMPLE),
                                    @ExampleObject(
                                            name = "DUPLICATE_PHONE_NUMBER",
                                            value =
                                                    AuthSwaggerExamples
                                                            .DUPLICATE_PHONE_NUMBER_EXAMPLE),
                                    @ExampleObject(
                                            name = "DUPLICATE_NICKNAME",
                                            value = AuthSwaggerExamples.DUPLICATE_NICKNAME_EXAMPLE)
                                }))
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 검증한 뒤 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "로그인 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": {
                                                            "accessToken": "access-token-value",
                                                            "refreshToken": "refresh-token-value",
                                                            "tokenType": "Bearer"
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "VALIDATION_ERROR",
                                                value =
                                                        AuthSwaggerExamples
                                                                .VALIDATION_ERROR_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "로그인 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INVALID_LOGIN",
                                                value =
                                                        AuthSwaggerExamples
                                                                .INVALID_LOGIN_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "계정 상태로 인한 로그인 차단",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "SUSPENDED_USER",
                                            value = AuthSwaggerExamples.SUSPENDED_USER_EXAMPLE),
                                    @ExampleObject(
                                            name = "WITHDRAWN_USER",
                                            value = AuthSwaggerExamples.WITHDRAWN_USER_EXAMPLE)
                                }))
    })
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token을 검증한 뒤 새로운 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "토큰 재발급 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": {
                                                            "accessToken": "new-access-token-value",
                                                            "refreshToken": "new-refresh-token-value",
                                                            "tokenType": "Bearer"
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "VALIDATION_ERROR",
                                                value =
                                                        AuthSwaggerExamples
                                                                .VALIDATION_ERROR_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Refresh Token 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = AuthSwaggerExamples.INVALID_TOKEN_EXAMPLE),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = AuthSwaggerExamples.EXPIRED_TOKEN_EXAMPLE),
                                    @ExampleObject(
                                            name = "REVOKED_TOKEN",
                                            value = AuthSwaggerExamples.REVOKED_TOKEN_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "사용자 상태로 인한 재발급 차단",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "SUSPENDED_USER",
                                            value = AuthSwaggerExamples.SUSPENDED_USER_EXAMPLE),
                                    @ExampleObject(
                                            name = "WITHDRAWN_USER",
                                            value = AuthSwaggerExamples.WITHDRAWN_USER_EXAMPLE)
                                }))
    })
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@RequestBody @Valid TokenReissueRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }

    @Operation(summary = "로그아웃", description = "Access Token 인증을 요구하지 않으며 Refresh Token만으로 처리합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "로그아웃 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": null,
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "VALIDATION_ERROR",
                                                value =
                                                        AuthSwaggerExamples
                                                                .VALIDATION_ERROR_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 Refresh Token",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INVALID_TOKEN",
                                                value = AuthSwaggerExamples.INVALID_TOKEN_EXAMPLE)))
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.success(null);
    }
}
