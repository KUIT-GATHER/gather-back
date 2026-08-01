package com.gather.gather.domain.auth.kakao.admin.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 카카오 Admin key 방식의 연결 해제 HTTP 계약을 캡슐화한 전용 outbound client. */
public class KakaoAdminApiClient {

    private static final String UNLINK_PATH = "/v1/user/unlink";
    private static final String ADMIN_AUTHORIZATION_PREFIX = "KakaoAK ";
    private static final String TARGET_ID_TYPE = "user_id";
    private static final long MAX_RETRY_AFTER_SECONDS = 6 * 60 * 60;
    private static final int MAX_RETRY_AFTER_DELTA_DIGITS = 10;
    private static final MediaType FORM_URLENCODED_UTF8 =
            new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8);

    private static final int KAKAO_ALREADY_UNLINKED = -101;
    private static final int KAKAO_INTERNAL_ERROR = -1;
    private static final int KAKAO_INVALID_PARAMETER = -2;
    private static final int KAKAO_API_NOT_ENABLED = -3;
    private static final int KAKAO_ACCOUNT_RESTRICTED = -4;
    private static final int KAKAO_PERMISSION_DENIED = -5;
    private static final int KAKAO_OPERATION_NOT_ALLOWED = -6;
    private static final int KAKAO_SERVICE_UNAVAILABLE = -7;
    private static final int KAKAO_INVALID_HEADER = -8;
    private static final int KAKAO_API_RETIRED = -9;
    private static final int KAKAO_RATE_LIMITED = -10;
    private static final int KAKAO_PAID_QUOTA_EXCEEDED = -11;
    private static final int KAKAO_APP_RESTRICTED = -12;
    private static final int KAKAO_APP_DORMANT = -13;
    private static final int KAKAO_INVALID_CREDENTIALS = -401;
    private static final int KAKAO_PROVIDER_TIMEOUT = -603;
    private static final int KAKAO_UNREGISTERED_APP_KEY = -903;
    private static final int KAKAO_MAINTENANCE = -9798;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String adminKey;
    private final Clock clock;

    public KakaoAdminApiClient(
            RestClient restClient, ObjectMapper objectMapper, String adminKey, Clock clock) {
        this.restClient = restClient;
        this.objectMapper =
                objectMapper
                        .copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.adminKey = adminKey;
        this.clock = clock;
    }

    /**
     * 카카오 회원번호로 연결 해제를 한 번 요청한다.
     *
     * <p>이 메서드는 retry를 실행하지 않고 후속 worker가 판단할 typed 결과만 반환한다.
     */
    public KakaoAdminUnlinkResult unlink(long kakaoUserId) {
        if (kakaoUserId <= 0) {
            return result(KakaoAdminUnlinkDisposition.PERMANENT_REQUEST, null, null, null, null);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", TARGET_ID_TYPE);
        form.add("target_id", Long.toString(kakaoUserId));

        try {
            return restClient
                    .post()
                    .uri(UNLINK_PATH)
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION_PREFIX + adminKey)
                    .contentType(FORM_URLENCODED_UTF8)
                    .body(form)
                    .exchange((request, response) -> classify(response, kakaoUserId));
        } catch (RestClientException exception) {
            // 전송 예외의 원문 메시지나 cause는 민감정보를 포함할 수 있어 결과에 보관하지 않는다.
            return result(KakaoAdminUnlinkDisposition.RETRYABLE, null, null, null, null);
        }
    }

    private KakaoAdminUnlinkResult classify(ClientHttpResponse response, long requestedUserId)
            throws IOException {
        int httpStatus = response.getStatusCode().value();
        Instant responseReceivedAt = Instant.now(clock);

        if (response.getStatusCode().is2xxSuccessful()) {
            if (httpStatus != HttpStatus.OK.value()) {
                return result(
                        KakaoAdminUnlinkDisposition.RESPONSE_FAILURE,
                        httpStatus,
                        null,
                        response.getHeaders(),
                        responseReceivedAt);
            }
            return classifySuccess(response, requestedUserId, httpStatus, responseReceivedAt);
        }

        JsonReadResult jsonReadResult = readJson(response);
        if (jsonReadResult.isBodyReadFailure()) {
            return result(
                    KakaoAdminUnlinkDisposition.RETRYABLE,
                    httpStatus,
                    null,
                    response.getHeaders(),
                    responseReceivedAt);
        }

        Integer kakaoCode = readKakaoCode(jsonReadResult.body());
        KakaoAdminUnlinkDisposition knownDisposition = mapKnownKakaoCode(kakaoCode);
        if (knownDisposition != null) {
            return result(
                    knownDisposition,
                    httpStatus,
                    kakaoCode,
                    response.getHeaders(),
                    responseReceivedAt);
        }
        return classifyHttpFallback(
                httpStatus, kakaoCode, response.getHeaders(), responseReceivedAt);
    }

    private KakaoAdminUnlinkResult classifySuccess(
            ClientHttpResponse response,
            long requestedUserId,
            int httpStatus,
            Instant responseReceivedAt) {
        JsonReadResult jsonReadResult = readJson(response);
        if (jsonReadResult.isBodyReadFailure()) {
            return result(
                    KakaoAdminUnlinkDisposition.RETRYABLE,
                    httpStatus,
                    null,
                    response.getHeaders(),
                    responseReceivedAt);
        }

        JsonNode id = jsonReadResult.body() == null ? null : jsonReadResult.body().get("id");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
            return result(
                    KakaoAdminUnlinkDisposition.RESPONSE_FAILURE,
                    httpStatus,
                    null,
                    response.getHeaders(),
                    responseReceivedAt);
        }
        if (id.longValue() != requestedUserId) {
            return result(
                    KakaoAdminUnlinkDisposition.SECURITY_FAILURE,
                    httpStatus,
                    null,
                    response.getHeaders(),
                    responseReceivedAt);
        }
        return result(
                KakaoAdminUnlinkDisposition.SUCCESS,
                httpStatus,
                null,
                response.getHeaders(),
                responseReceivedAt);
    }

    private KakaoAdminUnlinkResult classifyHttpFallback(
            int httpStatus, Integer kakaoCode, HttpHeaders headers, Instant responseReceivedAt) {
        if (httpStatus == HttpStatus.TOO_MANY_REQUESTS.value()
                || (httpStatus >= 500 && httpStatus < 600)) {
            return result(
                    KakaoAdminUnlinkDisposition.RETRYABLE,
                    httpStatus,
                    kakaoCode,
                    headers,
                    responseReceivedAt);
        }
        return result(
                KakaoAdminUnlinkDisposition.UNKNOWN_PERMANENT,
                httpStatus,
                kakaoCode,
                headers,
                responseReceivedAt);
    }

    private KakaoAdminUnlinkDisposition mapKnownKakaoCode(Integer kakaoCode) {
        if (kakaoCode == null) {
            return null;
        }

        return switch (kakaoCode) {
            case KAKAO_ALREADY_UNLINKED -> KakaoAdminUnlinkDisposition.ALREADY_UNLINKED;
            case KAKAO_INTERNAL_ERROR,
                            KAKAO_SERVICE_UNAVAILABLE,
                            KAKAO_RATE_LIMITED,
                            KAKAO_PROVIDER_TIMEOUT,
                            KAKAO_MAINTENANCE ->
                    KakaoAdminUnlinkDisposition.RETRYABLE;
            case KAKAO_API_NOT_ENABLED,
                            KAKAO_PERMISSION_DENIED,
                            KAKAO_INVALID_HEADER,
                            KAKAO_PAID_QUOTA_EXCEEDED,
                            KAKAO_APP_RESTRICTED,
                            KAKAO_APP_DORMANT,
                            KAKAO_INVALID_CREDENTIALS,
                            KAKAO_UNREGISTERED_APP_KEY ->
                    KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION;
            case KAKAO_INVALID_PARAMETER,
                            KAKAO_ACCOUNT_RESTRICTED,
                            KAKAO_OPERATION_NOT_ALLOWED,
                            KAKAO_API_RETIRED ->
                    KakaoAdminUnlinkDisposition.PERMANENT_REQUEST;
            default -> null;
        };
    }

    private Integer readKakaoCode(JsonNode body) {
        JsonNode code = body == null ? null : body.get("code");
        if (code == null || !code.isIntegralNumber() || !code.canConvertToInt()) {
            return null;
        }
        return code.intValue();
    }

    private JsonReadResult readJson(ClientHttpResponse response) {
        try {
            return JsonReadResult.success(objectMapper.readTree(response.getBody()));
        } catch (JsonProcessingException exception) {
            return JsonReadResult.malformedJson();
        } catch (IOException exception) {
            return JsonReadResult.bodyReadFailure();
        }
    }

    private record JsonReadResult(JsonNode body, boolean isBodyReadFailure) {

        private static JsonReadResult success(JsonNode body) {
            return new JsonReadResult(body, false);
        }

        private static JsonReadResult malformedJson() {
            return new JsonReadResult(null, false);
        }

        private static JsonReadResult bodyReadFailure() {
            return new JsonReadResult(null, true);
        }
    }

    private KakaoAdminUnlinkResult result(
            KakaoAdminUnlinkDisposition disposition,
            Integer httpStatus,
            Integer kakaoCode,
            HttpHeaders headers,
            Instant responseReceivedAt) {
        Instant retryAfterAt = null;
        if (disposition == KakaoAdminUnlinkDisposition.RETRYABLE
                && httpStatus != null
                && (httpStatus == HttpStatus.TOO_MANY_REQUESTS.value()
                        || (httpStatus >= 500 && httpStatus < 600))) {
            retryAfterAt = parseRetryAfter(headers, responseReceivedAt);
        }
        return KakaoAdminUnlinkResult.of(disposition, httpStatus, kakaoCode, retryAfterAt);
    }

    private Instant parseRetryAfter(HttpHeaders headers, Instant responseReceivedAt) {
        if (headers == null || responseReceivedAt == null) {
            return null;
        }
        List<String> values = headers.get(HttpHeaders.RETRY_AFTER);
        if (values == null || values.size() != 1) {
            return null;
        }
        String value = values.get(0);
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        if (value.chars().allMatch(KakaoAdminApiClient::isAsciiDigit)) {
            if (value.length() > MAX_RETRY_AFTER_DELTA_DIGITS) {
                return null;
            }
            try {
                long seconds = Long.parseLong(value);
                long cappedSeconds = Math.min(seconds, MAX_RETRY_AFTER_SECONDS);
                return responseReceivedAt.plusSeconds(cappedSeconds);
            } catch (ArithmeticException | NumberFormatException exception) {
                return null;
            }
        }
        try {
            Instant retryAfterAt =
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            if (retryAfterAt.isBefore(responseReceivedAt)) {
                return null;
            }
            Instant cappedAt = responseReceivedAt.plusSeconds(MAX_RETRY_AFTER_SECONDS);
            return retryAfterAt.isAfter(cappedAt) ? cappedAt : retryAfterAt;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static boolean isAsciiDigit(int character) {
        return character >= '0' && character <= '9';
    }
}
