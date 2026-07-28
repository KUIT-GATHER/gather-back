package com.gather.gather.domain.auth.kakao.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@Component
public class KakaoApiClient {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final String USER_INFO_PATH = "/v2/user/me";
    private static final String UNLINK_PATH = "/v1/user/unlink";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_KEY_PREFIX = "KakaoAK ";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String restApiKey;
    private final String clientSecret;
    private final String adminKey;
    private final ObjectMapper objectMapper;

    @Autowired
    public KakaoApiClient(
            RestClient.Builder restClientBuilder,
            KakaoProperties properties,
            ObjectMapper objectMapper) {
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
                properties,
                objectMapper);
    }

    KakaoApiClient(RestClient authClient, RestClient apiClient, KakaoProperties properties) {
        this(authClient, apiClient, properties, new ObjectMapper());
    }

    KakaoApiClient(
            RestClient authClient,
            RestClient apiClient,
            KakaoProperties properties,
            ObjectMapper objectMapper) {
        this.authClient = authClient;
        this.apiClient = apiClient;
        this.restApiKey = properties.restApiKey();
        this.clientSecret = properties.clientSecret();
        this.adminKey = properties.adminKey();
        this.objectMapper = objectMapper;
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        return ClientHttpRequestFactoryBuilder.simple()
                .build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT));
    }

    public String requestAccessToken(String authorizationCode, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
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
                        "token exchange");
        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()) {
            log.error("Kakao token response does not contain an access token.");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return response.accessToken();
    }

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
                        "user-info request");
        if (response == null || response.id() == null) {
            log.error("Kakao user-info response does not contain an id.");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    public KakaoUnlinkResult unlink(String providerUserId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);

        try {
            KakaoUnlinkResponse response =
                    apiClient
                            .post()
                            .uri(UNLINK_PATH)
                            .header(HttpHeaders.AUTHORIZATION, ADMIN_KEY_PREFIX + adminKey)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .exchange(
                                    (request, clientResponse) ->
                                            new KakaoUnlinkResponse(
                                                    clientResponse.getStatusCode(),
                                                    readBody(clientResponse)));
            return classifyUnlinkResponse(response);
        } catch (RestClientException exception) {
            log.error("Kakao unlink request failed. providerUserId={}", providerUserId, exception);
            return KakaoUnlinkResult.RETRYABLE_FAILURE;
        }
    }

    private KakaoUnlinkResult classifyUnlinkResponse(KakaoUnlinkResponse response) {
        if (response.status().is2xxSuccessful()) {
            return KakaoUnlinkResult.SUCCESS;
        }

        Integer errorCode = parseKakaoErrorCode(response.body());
        log.warn("Kakao unlink rejected. status={}, code={}", response.status().value(), errorCode);
        if (response.status().value() == HttpStatus.BAD_REQUEST.value()
                && Integer.valueOf(-101).equals(errorCode)) {
            return KakaoUnlinkResult.ALREADY_UNLINKED;
        }

        return KakaoUnlinkResult.RETRYABLE_FAILURE;
    }

    private Integer parseKakaoErrorCode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root != null && root.path("code").canConvertToInt()
                    ? root.path("code").intValue()
                    : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private <T> T call(Supplier<T> apiCall, String operation) {
        try {
            return apiCall.get();
        } catch (RestClientException exception) {
            log.error("Kakao {} failed.", operation, exception);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void rejectAsBadRequest(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        log.warn(
                "Kakao rejected the request. status={}, body={}",
                response.getStatusCode(),
                readBody(response));
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private void rejectAsRateLimited(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        log.error(
                "Kakao rate limit response. status={}, path={}, body={}",
                response.getStatusCode(),
                request.getURI().getPath(),
                readBody(response));
        throw new BusinessException(ErrorCode.KAKAO_API_UNAVAILABLE);
    }

    private void rejectAsServerError(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        log.error(
                "Kakao server error. status={}, body={}",
                response.getStatusCode(),
                readBody(response));
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private String readBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "<unavailable>";
        }
    }

    private record KakaoUnlinkResponse(HttpStatusCode status, String body) {}
}
