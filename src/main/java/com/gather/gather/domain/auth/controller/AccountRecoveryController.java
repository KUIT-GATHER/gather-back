package com.gather.gather.domain.auth.controller;

import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.domain.auth.dto.AccountRecoveryResponse;
import com.gather.gather.domain.auth.dto.PasswordResetAuthorityRequest;
import com.gather.gather.domain.auth.dto.PasswordResetRequest;
import com.gather.gather.domain.auth.dto.PasswordResetTokenResponse;
import com.gather.gather.domain.auth.service.AccountRecoveryService;
import com.gather.gather.domain.auth.service.PasswordResetService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증, 회원가입, 토큰 관련 API")
@RestController
@RequestMapping("/api/v1/auth/account-recoveries")
@RequiredArgsConstructor
public class AccountRecoveryController {

    private static final String JSON = "application/json";

    private final AccountRecoveryService accountRecoveryService;
    private final PasswordResetService passwordResetService;

    @Operation(
            summary = "아이디 찾기",
            description = "FIND_ACCOUNT 목적으로 완료한 휴대폰 인증을 소비하고 로그인 방식을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "아이디 찾기 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "EMAIL",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "loginType": "EMAIL",
                                                        "email": "user@example.com"
                                                      },
                                                      "error": null
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "KAKAO",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "loginType": "KAKAO",
                                                        "email": null
                                                      },
                                                      "error": null
                                                    }
                                                    """)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "인증 미완료, 만료, 소비 완료 또는 목적 불일치"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "인증 세션 또는 복구 가능한 계정 없음")
    })
    @PostMapping("/email")
    public ApiResponse<AccountRecoveryResponse> recoverEmail(
            @RequestBody @Valid AccountRecoveryRequest request) {
        return ApiResponse.success(accountRecoveryService.recoverEmail(request));
    }

    @Operation(
            summary = "비밀번호 재설정 권한 발급",
            description =
                    """
                    RESET_PASSWORD 목적으로 완료한 휴대폰 인증을 소비하고 10분간 유효한 일회용 재설정 토큰을 발급합니다.

                    - 인증(Access Token)이 필요하지 않습니다.
                    - 전화번호·이메일을 다시 받지 않고, phoneVerificationId가 가리키는 인증 결과만 사용합니다.
                    - phoneVerificationId는 성공·실패(카카오 전용/계정 없음) 모두 1회만 소비됩니다.
                    - 재발급하면 직전 토큰은 즉시 무효화되어 사용자당 1개만 유효합니다.
                    - 카카오 전용 계정은 비밀번호가 없으므로 409 PASSWORD_RESET_NOT_AVAILABLE입니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "재설정 토큰 발급 성공",
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
                                                            "passwordResetToken": "Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MGFiY2RlZmdoaWo",
                                                            "expiresAt": "2026-08-18T04:10:00"
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "인증 미완료, 만료, 소비 완료 또는 목적 불일치"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "인증 세션 또는 복구 가능한 계정 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "비밀번호 재설정을 지원하지 않는 계정(PASSWORD_RESET_NOT_AVAILABLE)")
    })
    @PostMapping("/password")
    public ApiResponse<PasswordResetTokenResponse> issuePasswordResetToken(
            @RequestBody @Valid PasswordResetAuthorityRequest request) {
        return ApiResponse.success(passwordResetService.issueToken(request));
    }

    @Operation(
            summary = "비밀번호 재설정",
            description =
                    """
                    재설정 토큰으로 새 비밀번호를 저장합니다.

                    - 인증(Access Token)이 필요하지 않습니다.
                    - 비밀번호는 공백 없이 6자 이상 12자 이하이며 passwordConfirm과 같아야 합니다.
                    - 토큰은 1회용이라 성공 시 삭제되며, 해당 계정의 Refresh Token이 모두 폐기됩니다.
                    - 새 Access/Refresh Token은 발급하지 않으므로 새 비밀번호로 다시 로그인해야 합니다.
                    - 이미 발급된 Access Token은 stateless JWT라 만료 전까지 즉시 폐기되지 않습니다.
                    - PASSWORD_RESET_TOKEN_INVALID와 PASSWORD_RESET_TOKEN_EXPIRED는 모두 본인인증부터 다시 시작해야 합니다.
                      만료된 토큰이 파기 배치로 이미 삭제된 경우 EXPIRED 대신 INVALID가 반환될 수 있습니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "비밀번호 재설정 성공",
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
                description = "비밀번호 정책 위반(VALIDATION_ERROR) 또는 확인 불일치(PASSWORD_MISMATCH)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description =
                        "재설정 토큰이 유효하지 않음(PASSWORD_RESET_TOKEN_INVALID) 또는 만료(PASSWORD_RESET_TOKEN_EXPIRED)")
    })
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.success(null);
    }
}
