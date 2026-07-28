package com.gather.gather.domain.auth.kakao.controller;

import com.gather.gather.domain.auth.kakao.service.KakaoUnlinkWebhookService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Kakao Webhook", description = "Kakao unlink webhook")
@RestController
@RequiredArgsConstructor
public class KakaoUnlinkWebhookController {

    private static final long SLOW_RESPONSE_THRESHOLD_MILLIS = 1000;

    private final KakaoUnlinkWebhookService kakaoUnlinkWebhookService;

    @Operation(
            summary = "Kakao unlink webhook",
            description =
                    "A missing or invalid admin key returns 401. Authenticated requests return 200, including internal processing failures.")
    @RequestMapping(
            value = "/api/v1/webhooks/kakao/unlink",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> handleUnlink(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                    String authorizationHeader,
            @RequestParam("app_id") String appId,
            @RequestParam("user_id") String userId,
            @RequestParam("referrer_type") String referrerType) {
        long startedAt = System.currentTimeMillis();
        try {
            kakaoUnlinkWebhookService.handleUnlink(authorizationHeader, appId, userId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                throw exception;
            }
            log.error(
                    "Kakao unlink webhook failed after authentication. appId={}, kakaoUserId={}, referrerType={}",
                    appId,
                    userId,
                    referrerType,
                    exception);
        } catch (RuntimeException exception) {
            log.error(
                    "Kakao unlink webhook failed after authentication. appId={}, kakaoUserId={}, referrerType={}",
                    appId,
                    userId,
                    referrerType,
                    exception);
        } finally {
            long elapsed = System.currentTimeMillis() - startedAt;
            if (elapsed >= SLOW_RESPONSE_THRESHOLD_MILLIS) {
                log.warn(
                        "Kakao unlink webhook processing was slow. elapsedMillis={}, appId={}, kakaoUserId={}, referrerType={}",
                        elapsed,
                        appId,
                        userId,
                        referrerType);
            }
        }
        return ResponseEntity.ok().build();
    }
}
