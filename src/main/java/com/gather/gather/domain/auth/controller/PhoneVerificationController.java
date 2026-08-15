package com.gather.gather.domain.auth.controller;

import com.gather.gather.domain.auth.dto.PhoneVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationQrCodeResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationStartRequest;
import com.gather.gather.domain.auth.dto.PhoneVerificationStartResponse;
import com.gather.gather.domain.auth.service.PhoneVerificationService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증, 회원가입, 토큰 관련 API")
@RestController
@RequestMapping("/api/v1/auth/phone-verifications")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private static final String JSON = "application/json";

    private final PhoneVerificationService phoneVerificationService;

    @Operation(
            summary = "휴대폰 문자 인증 시작",
            description = "인증 세션을 만들고 모바일 문자 앱 호출에 필요한 대표번호와 문자 원문을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "인증 세션 생성 성공",
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
                                                            "verificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                                                            "receiverNumber": "16663538",
                                                            "messageText": "[Gather]\\n전화번호 인증을 위한 문자입니다.\\n본 문자를 전송하시면 전화번호 인증이 자동으로 완료됩니다.\\n\\n인증코드: [GATHER-7F2K9Q8M4P]",
                                                            "expiresAt": "2026-08-13T01:05:00Z"
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "010으로 시작하는 11자리 휴대폰 번호가 아님",
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
                responseCode = "429",
                description = "같은 번호의 인증 시작 요청 간격 제한",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PHONE_VERIFICATION_RATE_LIMITED",
                                                value =
                                                        AuthSwaggerExamples
                                                                .PHONE_VERIFICATION_RATE_LIMITED_EXAMPLE)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PhoneVerificationStartResponse>> start(
            @RequestBody @Valid PhoneVerificationStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(phoneVerificationService.start(request)));
    }

    @Operation(
            summary = "PC용 휴대폰 인증 QR 발급",
            description = "서버에 저장된 인증문구로 OCTOMO SMS QR data URL을 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "QR 발급 성공",
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
                                                            "qrCode": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "verificationId 형식 오류 또는 인증 세션 만료",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value = AuthSwaggerExamples.VALIDATION_ERROR_EXAMPLE),
                                    @ExampleObject(
                                            name = "PHONE_VERIFICATION_EXPIRED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .PHONE_VERIFICATION_EXPIRED_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "인증 세션 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PHONE_VERIFICATION_NOT_FOUND",
                                                value =
                                                        AuthSwaggerExamples
                                                                .PHONE_VERIFICATION_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "QR 발급 횟수·간격 제한 또는 OCTOMO 요청 제한",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PHONE_VERIFICATION_RATE_LIMITED",
                                                value =
                                                        AuthSwaggerExamples
                                                                .PHONE_VERIFICATION_RATE_LIMITED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "OCTOMO 설정·네트워크·서비스 장애",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PHONE_VERIFICATION_PROVIDER_UNAVAILABLE",
                                                value =
                                                        AuthSwaggerExamples
                                                                .PHONE_VERIFICATION_PROVIDER_UNAVAILABLE_EXAMPLE)))
    })
    @PostMapping("/{verificationId}/qr-code")
    public ResponseEntity<ApiResponse<PhoneVerificationQrCodeResponse>> createQrCode(
            @PathVariable UUID verificationId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                phoneVerificationService.createQrCode(verificationId.toString())));
    }

    @Operation(
            summary = "휴대폰 문자 인증 확인",
            description = "OCTOMO에서 발신번호와 문자 원문의 수신 여부를 확인해 PENDING 또는 VERIFIED를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "문자 수신 대기 또는 휴대폰 인증 완료",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "PENDING",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": { "status": "PENDING" },
                                                      "error": null
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "VERIFIED",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": { "status": "VERIFIED" },
                                                      "error": null
                                                    }
                                                    """)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "verificationId 형식 오류 또는 인증 세션 만료",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "VALIDATION_ERROR",
                                            value = AuthSwaggerExamples.VALIDATION_ERROR_EXAMPLE),
                                    @ExampleObject(
                                            name = "PHONE_VERIFICATION_EXPIRED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .PHONE_VERIFICATION_EXPIRED_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "인증 세션 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PHONE_VERIFICATION_NOT_FOUND",
                                                value =
                                                        AuthSwaggerExamples
                                                                .PHONE_VERIFICATION_NOT_FOUND_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 가입된 전화번호 또는 탈퇴 후 재가입 제한 중인 전화번호",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "DUPLICATE_PHONE_NUMBER",
                                            value =
                                                    AuthSwaggerExamples
                                                            .DUPLICATE_PHONE_NUMBER_EXAMPLE),
                                    @ExampleObject(
                                            name = "ACCOUNT_REJOIN_BLOCKED",
                                            value =
                                                    AuthSwaggerExamples
                                                            .ACCOUNT_REJOIN_BLOCKED_EXAMPLE)
                                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "확인 횟수·간격 제한 또는 OCTOMO 요청 제한",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PHONE_VERIFICATION_RATE_LIMITED",
                                                value =
                                                        AuthSwaggerExamples
                                                                .PHONE_VERIFICATION_RATE_LIMITED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "OCTOMO 설정·네트워크·서비스 장애",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PHONE_VERIFICATION_PROVIDER_UNAVAILABLE",
                                                value =
                                                        AuthSwaggerExamples
                                                                .PHONE_VERIFICATION_PROVIDER_UNAVAILABLE_EXAMPLE)))
    })
    @PostMapping("/{verificationId}/confirm")
    public ApiResponse<PhoneVerificationConfirmResponse> confirm(
            @PathVariable UUID verificationId) {
        return ApiResponse.success(phoneVerificationService.confirm(verificationId.toString()));
    }
}
