package com.gather.gather.domain.auth.controller;

import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.domain.auth.dto.AccountRecoveryResponse;
import com.gather.gather.domain.auth.service.AccountRecoveryService;
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
}
