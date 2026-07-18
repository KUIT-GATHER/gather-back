package com.gather.gather.domain.auth.kakao.controller;

import com.gather.gather.domain.auth.dto.TokenResponse;
import com.gather.gather.domain.auth.kakao.dto.KakaoLoginRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoLoginResponse;
import com.gather.gather.domain.auth.kakao.dto.KakaoSignupRequest;
import com.gather.gather.domain.auth.kakao.service.KakaoAuthService;
import com.gather.gather.domain.auth.kakao.service.KakaoLoginResult;
import com.gather.gather.domain.auth.service.RefreshTokenCookieProvider;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth - Kakao", description = "카카오 로그인 및 카카오 회원가입 API")
@RestController
@RequestMapping("/api/v1/auth/kakao")
@RequiredArgsConstructor
public class KakaoAuthController {

    private static final String JSON = "application/json";
    private static final String SIGNUP_TOKEN_HEADER = "X-Signup-Token";

    private final KakaoAuthService kakaoAuthService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Operation(
            summary = "카카오 로그인",
            description =
                    """
                    카카오 인가 코드를 검증한 뒤 기존 회원이면 로그인시키고, 신규 회원이면 가입용 임시 토큰을 발급합니다.
                    두 경우 모두 HTTP 200이며 signupStatus로 구분합니다. ADDITIONAL_INFO_REQUIRED는 에러가 아닙니다.

                    400(인가 코드 무효·재사용, redirectUri 불일치)과 500(카카오 장애)은 코드값을 보지 말고 모두
                    '카카오 로그인 다시 시작'으로 처리하세요. 단 403(정지·탈퇴)은 기존 로그인과 동일하게 계정 상태를
                    안내해야 하며 재시작으로 처리하지 않습니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "카카오 인증 성공. 기존 회원은 로그인 완료, 신규 회원은 추가정보 입력 필요.",
                headers =
                        @Header(
                                name = "Set-Cookie",
                                description = "LOGIN_COMPLETED일 때만 발급되는 HttpOnly Refresh Token 쿠키",
                                schema =
                                        @Schema(
                                                type = "string",
                                                example =
                                                        "gather_refresh_token=refresh-token-value; Path=/api/v1/auth; Max-Age=1209600; HttpOnly; SameSite=Lax")),
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "LOGIN_COMPLETED",
                                            summary = "기존 카카오 회원 로그인 완료",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "signupStatus": "LOGIN_COMPLETED",
                                                        "accessToken": "access-token-value",
                                                        "tokenType": "Bearer"
                                                      },
                                                      "error": null
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "ADDITIONAL_INFO_REQUIRED",
                                            summary = "신규 카카오 회원. 추가정보 입력 필요.",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "signupStatus": "ADDITIONAL_INFO_REQUIRED",
                                                        "signupToken": "signup-token-value",
                                                        "profile": {
                                                          "nickname": "동현"
                                                        }
                                                      },
                                                      "error": null
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "ADDITIONAL_INFO_REQUIRED (닉네임 없음)",
                                            summary = "카카오 닉네임을 가져오지 못한 경우",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "signupStatus": "ADDITIONAL_INFO_REQUIRED",
                                                        "signupToken": "signup-token-value",
                                                        "profile": {
                                                          "nickname": null
                                                        }
                                                      },
                                                      "error": null
                                                    }
                                                    """)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패, 인가 코드 무효·재사용, 또는 허용되지 않은 redirectUri",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "VALIDATION_ERROR",
                                                value =
                                                        KakaoAuthSwaggerExamples
                                                                .VALIDATION_ERROR_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "기존 카카오 회원의 계정 상태로 인한 로그인 차단",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "SUSPENDED_USER",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .SUSPENDED_USER_EXAMPLE),
                                    @ExampleObject(
                                            name = "WITHDRAWN_USER",
                                            value = KakaoAuthSwaggerExamples.WITHDRAWN_USER_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "카카오 API 장애 또는 타임아웃",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INTERNAL_SERVER_ERROR",
                                                value =
                                                        KakaoAuthSwaggerExamples
                                                                .INTERNAL_SERVER_ERROR_EXAMPLE)))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<KakaoLoginResponse>> login(
            @RequestBody @Valid KakaoLoginRequest request) {
        KakaoLoginResult result = kakaoAuthService.login(request);

        if (!result.isLoginCompleted()) {
            return ResponseEntity.ok(
                    ApiResponse.success(
                            KakaoLoginResponse.additionalInfoRequired(
                                    result.signupToken(), result.nickname())));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.tokens()))
                .body(
                        ApiResponse.success(
                                KakaoLoginResponse.loginCompleted(result.tokens().accessToken())));
    }

    @Operation(
            summary = "카카오 추가정보 가입",
            description =
                    """
                    카카오 로그인에서 받은 가입용 임시 토큰과 추가정보로 회원가입을 완료하고 곧바로 로그인 토큰을 발급합니다.
                    이메일과 비밀번호는 받지 않습니다.

                    가입 토큰은 Authorization이 아니라 X-Signup-Token 헤더로 보냅니다.
                    전화번호가 이미 가입에 사용된 경우 DUPLICATE_PHONE_NUMBER(409)로 실패합니다. 이는 기존 일반 계정과
                    동일인이라는 뜻이므로, 입력 오류가 아니라 기존 계정 로그인 안내로 처리하세요.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "가입 및 자동 로그인 성공",
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
                description = "요청 값 검증 실패 또는 비즈니스 규칙 위반",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .VALIDATION_ERROR_EXAMPLE),
                                    @ExampleObject(
                                            name = "REQUIRED_TERMS_NOT_AGREED",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .REQUIRED_TERMS_NOT_AGREED_EXAMPLE),
                                    @ExampleObject(
                                            name = "INVALID_ACTIVITY_REGION",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .INVALID_ACTIVITY_REGION_EXAMPLE),
                                    @ExampleObject(
                                            name = "INVALID_INTEREST_CATEGORY_COUNT",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .INVALID_INTEREST_CATEGORY_COUNT_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "가입용 토큰 오류. signupToken을 지우고 카카오 로그인부터 다시 시작하세요.",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "SIGNUP_TOKEN_EXPIRED",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .SIGNUP_TOKEN_EXPIRED_EXAMPLE),
                                    @ExampleObject(
                                            name = "SIGNUP_TOKEN_INVALID",
                                            summary = "서명 변조·클레임 불일치·헤더 누락",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .SIGNUP_TOKEN_INVALID_EXAMPLE)
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
                                                        KakaoAuthSwaggerExamples
                                                                .REGION_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "중복 데이터 존재",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "DUPLICATE_PHONE_NUMBER",
                                            summary = "이미 가입된 전화번호. 기존 계정 로그인 안내가 필요합니다.",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .DUPLICATE_PHONE_NUMBER_EXAMPLE),
                                    @ExampleObject(
                                            name = "DUPLICATE_NICKNAME",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .DUPLICATE_NICKNAME_EXAMPLE),
                                    @ExampleObject(
                                            name = "ALREADY_REGISTERED",
                                            summary = "이미 가입된 카카오 계정. 가입 토큰 재사용.",
                                            value =
                                                    KakaoAuthSwaggerExamples
                                                            .ALREADY_REGISTERED_EXAMPLE)
                                }))
    })
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
            @Parameter(
                            name = SIGNUP_TOKEN_HEADER,
                            description = "카카오 로그인 응답으로 받은 가입용 임시 토큰",
                            required = true,
                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER)
                    @RequestHeader(name = SIGNUP_TOKEN_HEADER, required = false)
                    String signupToken,
            @RequestBody @Valid KakaoSignupRequest request) {
        TokenIssueResult tokens = kakaoAuthService.signup(signupToken, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens))
                .body(ApiResponse.success(TokenResponse.bearer(tokens.accessToken())));
    }

    private String refreshCookie(TokenIssueResult tokens) {
        return refreshTokenCookieProvider.create(tokens.refreshToken()).toString();
    }
}
