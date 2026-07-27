package com.gather.gather.domain.auth.kakao.controller;

import com.gather.gather.domain.auth.kakao.service.KakaoUnlinkWebhookService;
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

/**
 * 카카오 연결 해제 웹훅 수신 엔드포인트.
 *
 * <p>카카오 문서에 GET·POST 예제가 모두 있어 어느 쪽으로 올지 확정할 수 없으므로 둘 다 받는다. 본문은 JSON이 아니라 form-urlencoded(POST)와
 * 쿼리스트링(GET)이라 {@code @RequestParam}으로 받아야 한다.
 *
 * <p>{@code referrer_type}은 enum으로 변환하지 않는다. 카카오가 값을 추가하면 400으로 죽어 웹훅이 유실된다.
 */
@Slf4j
@Tag(name = "Kakao Webhook", description = "카카오 연결 해제 웹훅 (카카오 서버 전용)")
@RestController
@RequiredArgsConstructor
public class KakaoUnlinkWebhookController {

    // 카카오는 3초 안에 응답을 받지 못하면 실패로 보고 재전송을 보장하지 않는다. 여유가 줄어드는 조짐을 미리 보기 위한 임계치.
    private static final long SLOW_RESPONSE_THRESHOLD_MILLIS = 1000;

    private final KakaoUnlinkWebhookService kakaoUnlinkWebhookService;

    @Operation(
            summary = "카카오 연결 해제 웹훅",
            description =
                    "사용자가 카카오에서 서비스 연결을 끊었을 때 카카오가 호출합니다. 클라이언트가 호출하는 API가 아닙니다. "
                            + "어드민 키가 일치하지 않으면 401, 그 외에는 처리할 대상이 없어도 200을 반환합니다.")
    @RequestMapping(
            value = "/api/v1/webhooks/kakao/unlink",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> handleUnlink(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                    String authorizationHeader,
            @RequestParam("app_id") String appId,
            @RequestParam("user_id") String userId,
            @RequestParam("referrer_type") String referrerType) {
        log.info(
                "카카오 연결 해제 웹훅 수신: appId={}, kakaoUserId={}, referrerType={}",
                appId,
                userId,
                referrerType);

        long startedAt = System.currentTimeMillis();
        kakaoUnlinkWebhookService.handleUnlink(authorizationHeader, appId, userId);
        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed >= SLOW_RESPONSE_THRESHOLD_MILLIS) {
            log.warn("카카오 연결 해제 웹훅 처리가 느립니다. elapsedMillis={}, kakaoUserId={}", elapsed, userId);
        }

        // 카카오는 응답 본문을 보지 않으므로 ApiResponse로 감싸지 않는다.
        return ResponseEntity.ok().build();
    }
}
