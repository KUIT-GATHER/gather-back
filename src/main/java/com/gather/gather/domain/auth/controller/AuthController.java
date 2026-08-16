package com.gather.gather.domain.auth.controller;

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.EmailVerificationSendRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationSendResponse;
import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.PhoneNumberAvailabilityRequest;
import com.gather.gather.domain.auth.dto.PhoneNumberAvailabilityResponse;
import com.gather.gather.domain.auth.dto.SessionRestoreResponse;
import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.dto.SignupResponse;
import com.gather.gather.domain.auth.dto.TokenResponse;
import com.gather.gather.domain.auth.service.AuthService;
import com.gather.gather.domain.auth.service.RefreshTokenCookieProvider;
import com.gather.gather.domain.auth.service.SignupResult;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@Tag(name = "Auth", description = "인증, 회원가입, 토큰 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String JSON = "application/json";

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

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
                                                            "expiresAt": "2026-06-28T03:10:00",
                                                            "resendAvailableAt": "2026-06-28T03:03:00",
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
                responseCode = "429",
                description = "재발송 쿨다운 이내 재요청 또는 당일 발송 한도 초과",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "EMAIL_RESEND_TOO_SOON",
                                            value =
                                                    AuthSwaggerExamples
                                                            .EMAIL_RESEND_TOO_SOON_EXAMPLE),
                                    @ExampleObject(
                                            name = "EMAIL_SEND_LIMIT_EXCEEDED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .EMAIL_SEND_LIMIT_EXCEEDED_EXAMPLE)
                                })),
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
                                                            "verifiedAt": "2026-06-28T03:05:00",
                                                            "emailVerificationId": "98fa88ef-bbeb-4928-a202-7885197b3774"
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
                                                                .EMAIL_VERIFICATION_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "인증 코드 입력 시도 횟수 초과",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED",
                                                value =
                                                        AuthSwaggerExamples
                                                                .EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED_EXAMPLE)))
    })
    @PostMapping("/email-verifications/confirm")
    public ApiResponse<EmailVerificationConfirmResponse> confirmEmailVerificationCode(
            @RequestBody @Valid EmailVerificationConfirmRequest request) {
        return ApiResponse.success(authService.confirmEmailVerificationCode(request));
    }

    @Operation(
            summary = "전화번호 중복 확인",
            description =
                    "기존 클라이언트 호환을 위해 유지하는 API입니다. 신규 회원가입에서는 휴대폰 인증 API를 사용합니다. "
                            + "이미 가입에 사용됐거나 탈퇴 후 재가입 제한 중이면 available은 false입니다.")
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
                                            summary = "이미 사용 중이거나 재가입 제한 중인 전화번호",
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

    @Operation(
            summary = "회원가입",
            description =
                    "요청 전화번호와 일치하는 30분 이내 미소비 휴대폰 인증이 필요합니다. 회원가입 성공 시 Access Token은 응답 본문으로, "
                            + "Refresh Token은 HttpOnly 쿠키로 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "회원가입 및 자동 로그인 성공",
                headers =
                        @Header(
                                name = "Set-Cookie",
                                description = "HttpOnly Refresh Token 쿠키",
                                schema =
                                        @Schema(
                                                type = "string",
                                                example =
                                                        "gather_refresh_token=refresh-token-value; Path=/api/v1/auth; Max-Age=1209600; HttpOnly; SameSite=Lax")),
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
                                                            "nickname": "길동",
                                                            "accessToken": "access-token-value",
                                                            "tokenType": "Bearer"
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
                                            name = "EMAIL_VERIFICATION_REQUIRED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .EMAIL_VERIFICATION_REQUIRED_EXAMPLE),
                                    @ExampleObject(
                                            name = "PHONE_VERIFICATION_REQUIRED",
                                            summary = "인증 없음·ID/전화번호 불일치·인증 만료·이미 소비됨",
                                            value =
                                                    AuthSwaggerExamples
                                                            .PHONE_VERIFICATION_REQUIRED_EXAMPLE),
                                    @ExampleObject(
                                            name = "REQUIRED_TERMS_NOT_AGREED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .REQUIRED_TERMS_NOT_AGREED_EXAMPLE),
                                    @ExampleObject(
                                            name = "INVALID_INTEREST_CATEGORY_COUNT",
                                            value =
                                                    AuthSwaggerExamples
                                                            .INVALID_INTEREST_CATEGORY_COUNT_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "활동 지역 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "REGION_NOT_FOUND",
                                                value =
                                                        AuthSwaggerExamples
                                                                .REGION_NOT_FOUND_EXAMPLE))),
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
                                            value = AuthSwaggerExamples.DUPLICATE_NICKNAME_EXAMPLE),
                                    @ExampleObject(
                                            name = "ACCOUNT_REJOIN_BLOCKED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .ACCOUNT_REJOIN_BLOCKED_EXAMPLE)
                                }))
    })
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody @Valid SignupRequest request) {
        SignupResult result = authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.success(result.response()));
    }

    @Operation(
            summary = "로그인",
            description =
                    "이메일과 비밀번호를 검증한 뒤 Access Token은 응답 본문으로, Refresh Token은 HttpOnly 쿠키로 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "로그인 성공",
                headers =
                        @Header(
                                name = "Set-Cookie",
                                description = "HttpOnly Refresh Token 쿠키",
                                schema =
                                        @Schema(
                                                type = "string",
                                                example =
                                                        "gather_refresh_token=refresh-token-value; Path=/api/v1/auth; Max-Age=1209600; HttpOnly; SameSite=Lax")),
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
                                            name = "WITHDRAWAL_PENDING_USER",
                                            value =
                                                    AuthSwaggerExamples
                                                            .WITHDRAWAL_PENDING_USER_EXAMPLE),
                                    @ExampleObject(
                                            name = "WITHDRAWN_USER",
                                            value = AuthSwaggerExamples.WITHDRAWN_USER_EXAMPLE)
                                }))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request) {
        return tokenResponse(authService.login(request));
    }

    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token 쿠키를 검증한 뒤 새로운 Access Token과 Refresh Token 쿠키를 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "토큰 재발급 성공",
                headers =
                        @Header(
                                name = "Set-Cookie",
                                description = "rotation으로 새로 발급되는 HttpOnly Refresh Token 쿠키",
                                schema =
                                        @Schema(
                                                type = "string",
                                                example =
                                                        "gather_refresh_token=new-refresh-token-value; Path=/api/v1/auth; Max-Age=1209600; HttpOnly; SameSite=Lax")),
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
                                                            "tokenType": "Bearer"
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
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
                                            name = "WITHDRAWAL_PENDING_USER",
                                            value =
                                                    AuthSwaggerExamples
                                                            .WITHDRAWAL_PENDING_USER_EXAMPLE),
                                    @ExampleObject(
                                            name = "WITHDRAWN_USER",
                                            value = AuthSwaggerExamples.WITHDRAWN_USER_EXAMPLE)
                                }))
    })
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(HttpServletRequest request) {
        return tokenResponse(authService.reissue(extractRefreshToken(request)));
    }

    @Operation(
            summary = "로그인 세션 복원",
            description =
                    "앱 최초 로딩 시 Refresh Token 쿠키로 세션을 복원합니다. 유효한 세션은 Refresh Token을 rotation하며, 복원할 세션이 없으면 정상적인 비로그인 상태를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "세션 복원 성공 또는 복원할 세션 없음",
                headers =
                        @Header(
                                name = "Set-Cookie",
                                description =
                                        "세션 복원 성공 시에만 rotation으로 발급되는 HttpOnly Refresh Token 쿠키",
                                schema =
                                        @Schema(
                                                type = "string",
                                                example =
                                                        "gather_refresh_token=new-refresh-token-value; Path=/api/v1/auth; Max-Age=1209600; HttpOnly; SameSite=Lax")),
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "AUTHENTICATED",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "authenticated": true,
                                                        "accessToken": "new-access-token-value",
                                                        "tokenType": "Bearer"
                                                      },
                                                      "error": null
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "ANONYMOUS",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "authenticated": false,
                                                        "accessToken": null,
                                                        "tokenType": null
                                                      },
                                                      "error": null
                                                    }
                                                    """)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "사용자 상태로 인한 세션 복원 차단",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "SUSPENDED_USER",
                                            value = AuthSwaggerExamples.SUSPENDED_USER_EXAMPLE),
                                    @ExampleObject(
                                            name = "WITHDRAWAL_PENDING_USER",
                                            value =
                                                    AuthSwaggerExamples
                                                            .WITHDRAWAL_PENDING_USER_EXAMPLE),
                                    @ExampleObject(
                                            name = "WITHDRAWN_USER",
                                            value = AuthSwaggerExamples.WITHDRAWN_USER_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INTERNAL_SERVER_ERROR",
                                                value =
                                                        AuthSwaggerExamples
                                                                .INTERNAL_SERVER_ERROR_EXAMPLE)))
    })
    @PostMapping("/session/restore")
    public ResponseEntity<ApiResponse<SessionRestoreResponse>> restoreSession(
            HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, refreshTokenCookieProvider.cookieName());
        if (cookie == null) {
            return anonymousSessionRestoreResponse();
        }

        return authService
                .restoreSession(cookie.getValue())
                .map(this::authenticatedSessionRestoreResponse)
                .orElseGet(this::anonymousSessionRestoreResponse);
    }

    @Operation(summary = "로그아웃", description = "Access Token 인증을 요구하지 않으며 Refresh Token만으로 처리합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "로그아웃 성공",
                headers =
                        @Header(
                                name = "Set-Cookie",
                                description = "Refresh Token 삭제 쿠키",
                                schema =
                                        @Schema(
                                                type = "string",
                                                example =
                                                        "gather_refresh_token=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax")),
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
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.clear().toString());
        authService.logout(extractRefreshToken(request));
        return ApiResponse.success(null);
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(TokenIssueResult tokenResult) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.create(tokenResult.refreshToken()).toString())
                .body(ApiResponse.success(TokenResponse.bearer(tokenResult.accessToken())));
    }

    private ResponseEntity<ApiResponse<SessionRestoreResponse>> authenticatedSessionRestoreResponse(
            TokenIssueResult tokenResult) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.create(tokenResult.refreshToken()).toString())
                .body(
                        ApiResponse.success(
                                SessionRestoreResponse.authenticated(tokenResult.accessToken())));
    }

    private ResponseEntity<ApiResponse<SessionRestoreResponse>> anonymousSessionRestoreResponse() {
        return ResponseEntity.ok(ApiResponse.success(SessionRestoreResponse.anonymous()));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, refreshTokenCookieProvider.cookieName());
        if (cookie == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return cookie.getValue();
    }
}
