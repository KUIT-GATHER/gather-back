package com.gather.gather.domain.auth.kakao.client;

import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.dto.KakaoTokenResponse;
import com.gather.gather.domain.auth.kakao.dto.KakaoUserResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로그인 연동 클라이언트(토큰 교환, 사용자 정보 조회).
 *
 * <p>카카오의 4xx와 5xx를 구분해 서로 다른 {@link BusinessException}으로 변환한다. 인가 코드 재사용(KOE320)은 사용자가 콜백 화면을
 * 새로고침하거나 뒤로가기만 해도 발생하는 일상적인 흐름이라, 500으로 올리면 정상 사용이 서버 에러 알림을 울리게 된다.
 *
 * <p>카카오 Access Token과 client_secret은 어떤 경로로도 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoApiClient {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final String USER_INFO_PATH = "/v2/user/me";
    private static final String BEARER_PREFIX = "Bearer ";

    // 사용자가 응답을 기다리는 동기 엔드포인트라 카카오 지연이 그대로 사용자 대기로 이어진다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String restApiKey;
    private final String clientSecret;

    @Autowired
    public KakaoApiClient(RestClient.Builder restClientBuilder, KakaoProperties properties) {
        this(
                restClientBuilder
                        .clone()
                        .baseUrl(properties.authBaseUrl())
                        .requestFactory(timeoutRequestFactory())
                        .build(),
                restClientBuilder
                        .clone()
                        .baseUrl(properties.apiBaseUrl())
                        .requestFactory(timeoutRequestFactory())
                        .build(),
                properties);
    }

    // 테스트가 MockRestServiceServer로 만든 RestClient를 주입할 수 있도록 분리한 생성자.
    KakaoApiClient(RestClient authClient, RestClient apiClient, KakaoProperties properties) {
        this.authClient = authClient;
        this.apiClient = apiClient;
        this.restApiKey = properties.restApiKey();
        this.clientSecret = properties.clientSecret();
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        return ClientHttpRequestFactoryBuilder.simple()
                .build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT));
    }

    /** 인가 코드를 카카오 Access Token으로 교환한다. redirectUri는 인가 요청·콘솔 등록값과 정확히 같아야 한다(불일치 시 KOE006). */
    public String requestAccessToken(String authorizationCode, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
        // Client Secret이 활성화돼 있어 필수다. 누락 시 KOE010.
        form.add("client_secret", clientSecret);

        KakaoTokenResponse response =
                call(
                        () ->
                                authClient
                                        .post()
                                        .uri(TOKEN_PATH)
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .body(form)
                                        .retrieve()
                                        .onStatus(
                                                status ->
                                                        status.value()
                                                                == HttpStatus.TOO_MANY_REQUESTS
                                                                        .value(),
                                                this::rejectAsRateLimited)
                                        .onStatus(
                                                HttpStatusCode::is4xxClientError,
                                                this::rejectAsBadRequest)
                                        .onStatus(
                                                HttpStatusCode::is5xxServerError,
                                                this::rejectAsServerError)
                                        .body(KakaoTokenResponse.class),
                        "토큰 교환");

        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()) {
            log.error("카카오 토큰 교환 응답에 access_token이 없습니다.");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return response.accessToken();
    }

    /** 카카오 Access Token으로 회원번호와 닉네임을 조회한다. */
    public KakaoUserResponse getUserInfo(String kakaoAccessToken) {
        KakaoUserResponse response =
                call(
                        () ->
                                apiClient
                                        .get()
                                        .uri(USER_INFO_PATH)
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                BEARER_PREFIX + kakaoAccessToken)
                                        .retrieve()
                                        .onStatus(
                                                status ->
                                                        status.value()
                                                                == HttpStatus.TOO_MANY_REQUESTS
                                                                        .value(),
                                                this::rejectAsRateLimited)
                                        .onStatus(
                                                HttpStatusCode::is4xxClientError,
                                                this::rejectAsBadRequest)
                                        .onStatus(
                                                HttpStatusCode::is5xxServerError,
                                                this::rejectAsServerError)
                                        .body(KakaoUserResponse.class),
                        "사용자 정보 조회");

        // 회원번호는 사용자 식별의 유일한 근거이므로 없으면 진행할 수 없다.
        if (response == null || response.id() == null) {
            log.error("카카오 사용자 정보 응답에 회원번호가 없습니다.");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private <T> T call(Supplier<T> apiCall, String operation) {
        try {
            return apiCall.get();
        } catch (RestClientException exception) {
            // 네트워크 실패·타임아웃·응답 파싱 실패. 카카오 측 문제이므로 500.
            log.error("카카오 {} 호출에 실패했습니다.", operation, exception);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void rejectAsBadRequest(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        log.warn(
                "카카오가 요청을 거부했습니다. status={}, body={}",
                response.getStatusCode(),
                readBody(response));
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private void rejectAsRateLimited(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        log.error(
                "카카오 API 요청 제한이 발생했습니다. status={}, path={}, body={}",
                response.getStatusCode(),
                request.getURI().getPath(),
                readBody(response));
        throw new BusinessException(ErrorCode.KAKAO_API_UNAVAILABLE);
    }

    private void rejectAsServerError(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        log.error(
                "카카오 API가 5xx를 반환했습니다. status={}, body={}",
                response.getStatusCode(),
                readBody(response));
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 카카오 에러 응답 본문(error_code 등)은 원인 파악에 필요하고 토큰을 담지 않으므로 로그에 남긴다.
    private String readBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "<본문 읽기 실패>";
        }
    }
}
