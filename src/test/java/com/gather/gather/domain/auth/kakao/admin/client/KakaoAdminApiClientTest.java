package com.gather.gather.domain.auth.kakao.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class KakaoAdminApiClientTest {

    private static final String BASE_URL = "http://localhost/kakao-admin";
    private static final String TEST_KEY = "unit-test-admin-key";
    private static final long TARGET_ID = 41L;
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MediaType FORM_URLENCODED_UTF8 =
            new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private KakaoAdminApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoAdminApiClient(builder.build(), new ObjectMapper(), TEST_KEY, CLOCK);
    }

    @Test
    void unlink_withMatchingId_returnsSuccess() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("target_id_type", "user_id");
        expectedForm.add("target_id", Long.toString(TARGET_ID));

        server.expect(requestTo(BASE_URL + "/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + TEST_KEY))
                .andExpect(content().contentType(FORM_URLENCODED_UTF8))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("{\"id\":" + TARGET_ID + "}", MediaType.APPLICATION_JSON));

        assertResult(client.unlink(TARGET_ID), KakaoAdminUnlinkDisposition.SUCCESS, 200, null);
        server.verify();
    }

    @Test
    void unlink_withMismatchedId_returnsSecurityFailure() {
        respond(HttpStatus.OK, "{\"id\":42}");

        KakaoAdminUnlinkResult result = client.unlink(TARGET_ID);

        assertResult(result, KakaoAdminUnlinkDisposition.SECURITY_FAILURE, 200, null);
        assertThat(result.toString()).doesNotContain("41", "42");
        server.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSuccessResponses")
    void unlink_withInvalidSuccessBody_returnsResponseFailure(String name, String body) {
        respond(HttpStatus.OK, body);

        assertResult(
                client.unlink(TARGET_ID), KakaoAdminUnlinkDisposition.RESPONSE_FAILURE, 200, null);
        server.verify();
    }

    static Stream<Arguments> invalidSuccessResponses() {
        return Stream.of(
                Arguments.of("empty body", ""),
                Arguments.of("malformed JSON", "{"),
                Arguments.of("missing id", "{}"),
                Arguments.of("non-numeric id", "{\"id\":\"41\"}"),
                Arguments.of("zero id", "{\"id\":0}"),
                Arguments.of("negative id", "{\"id\":-1}"),
                Arguments.of("overflow id", "{\"id\":9223372036854775808}"),
                Arguments.of("duplicate id", "{\"id\":42,\"id\":41}"),
                Arguments.of("trailing token", "{\"id\":41} garbage"));
    }

    @Test
    void unlink_withUnexpected2xx_returnsResponseFailure() {
        respond(HttpStatus.CREATED, "{\"id\":41}");

        assertResult(
                client.unlink(TARGET_ID), KakaoAdminUnlinkDisposition.RESPONSE_FAILURE, 201, null);
        server.verify();
    }

    @ParameterizedTest(name = "Kakao code {0} -> {2}")
    @MethodSource("documentedKakaoCodeMappings")
    void unlink_withDocumentedKakaoCode_returnsMappedDisposition(
            int kakaoCode, HttpStatus httpStatus, KakaoAdminUnlinkDisposition expectedDisposition) {
        respond(httpStatus, "{\"code\":" + kakaoCode + "}");

        assertResult(client.unlink(TARGET_ID), expectedDisposition, httpStatus.value(), kakaoCode);
        server.verify();
    }

    static Stream<Arguments> documentedKakaoCodeMappings() {
        return Stream.of(
                Arguments.of(
                        -101, HttpStatus.BAD_REQUEST, KakaoAdminUnlinkDisposition.ALREADY_UNLINKED),
                Arguments.of(-1, HttpStatus.BAD_REQUEST, KakaoAdminUnlinkDisposition.RETRYABLE),
                Arguments.of(-7, HttpStatus.BAD_REQUEST, KakaoAdminUnlinkDisposition.RETRYABLE),
                Arguments.of(-10, HttpStatus.BAD_REQUEST, KakaoAdminUnlinkDisposition.RETRYABLE),
                Arguments.of(-603, HttpStatus.BAD_REQUEST, KakaoAdminUnlinkDisposition.RETRYABLE),
                Arguments.of(
                        -9798,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        KakaoAdminUnlinkDisposition.RETRYABLE),
                Arguments.of(
                        -2, HttpStatus.BAD_REQUEST, KakaoAdminUnlinkDisposition.PERMANENT_REQUEST),
                Arguments.of(
                        -4, HttpStatus.FORBIDDEN, KakaoAdminUnlinkDisposition.PERMANENT_REQUEST),
                Arguments.of(
                        -6, HttpStatus.FORBIDDEN, KakaoAdminUnlinkDisposition.PERMANENT_REQUEST),
                Arguments.of(
                        -9, HttpStatus.BAD_REQUEST, KakaoAdminUnlinkDisposition.PERMANENT_REQUEST),
                Arguments.of(
                        -3,
                        HttpStatus.FORBIDDEN,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        -5,
                        HttpStatus.FORBIDDEN,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        -8,
                        HttpStatus.BAD_REQUEST,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        -11,
                        HttpStatus.BAD_REQUEST,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        -12,
                        HttpStatus.FORBIDDEN,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        -13,
                        HttpStatus.BAD_REQUEST,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        -401,
                        HttpStatus.UNAUTHORIZED,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        -903,
                        HttpStatus.BAD_REQUEST,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION));
    }

    @ParameterizedTest(name = "HTTP {0}, code {1} -> {2}")
    @MethodSource("knownCodesOverrideHttpFallback")
    @DisplayName("Known Kakao codes override HTTP fallback")
    void unlink_withKnownCode_overridesHttpFallback(
            HttpStatus httpStatus, int kakaoCode, KakaoAdminUnlinkDisposition expectedDisposition) {
        respond(httpStatus, "{\"code\":" + kakaoCode + "}");

        assertResult(client.unlink(TARGET_ID), expectedDisposition, httpStatus.value(), kakaoCode);
        server.verify();
    }

    static Stream<Arguments> knownCodesOverrideHttpFallback() {
        return Stream.of(
                Arguments.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        -401,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        -3,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        -2,
                        KakaoAdminUnlinkDisposition.PERMANENT_REQUEST),
                Arguments.of(
                        HttpStatus.TOO_MANY_REQUESTS, -1, KakaoAdminUnlinkDisposition.RETRYABLE),
                Arguments.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        -401,
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION),
                Arguments.of(HttpStatus.BAD_REQUEST, -1, KakaoAdminUnlinkDisposition.RETRYABLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rateLimitFallbackBodies")
    void unlink_withUnparseableOrUnknownRateLimitBody_returnsRetryable(
            String name, String body, Integer expectedCode) {
        respond(HttpStatus.TOO_MANY_REQUESTS, body);

        assertResult(
                client.unlink(TARGET_ID), KakaoAdminUnlinkDisposition.RETRYABLE, 429, expectedCode);
        server.verify();
    }

    static Stream<Arguments> rateLimitFallbackBodies() {
        return Stream.of(
                Arguments.of("unknown code", "{\"code\":-7777}", -7777),
                Arguments.of("empty body", "", null),
                Arguments.of("malformed body", "{", null));
    }

    @Test
    void unlink_withDuplicateErrorCode_usesHttpFallback() {
        respond(HttpStatus.BAD_REQUEST, "{\"code\":-3,\"code\":-1}");

        assertResult(
                client.unlink(TARGET_ID), KakaoAdminUnlinkDisposition.UNKNOWN_PERMANENT, 400, null);
        server.verify();
    }

    @Test
    void unlink_withTrailingErrorToken_usesHttpFallback() {
        respond(HttpStatus.BAD_REQUEST, "{\"code\":-3} {\"code\":-1}");

        assertResult(
                client.unlink(TARGET_ID), KakaoAdminUnlinkDisposition.UNKNOWN_PERMANENT, 400, null);
        server.verify();
    }

    @Test
    void unlink_withUnknown4xx_returnsUnknownPermanent() {
        respond(HttpStatus.I_AM_A_TEAPOT, "{\"code\":-7777}");

        assertResult(
                client.unlink(TARGET_ID),
                KakaoAdminUnlinkDisposition.UNKNOWN_PERMANENT,
                418,
                -7777);
        server.verify();
    }

    @Test
    void unlink_withUnknown5xx_returnsRetryableWithoutInternalRetry() {
        respond(HttpStatus.BAD_GATEWAY, "{\"code\":-7777}");

        assertResult(client.unlink(TARGET_ID), KakaoAdminUnlinkDisposition.RETRYABLE, 502, -7777);
        server.verify();
    }

    @Test
    void unlink_withDeltaSecondsRetryAfter_returnsAbsoluteRetryTime() {
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{\"code\":-7777}", "120");

        KakaoAdminUnlinkResult result = client.unlink(TARGET_ID);

        assertThat(result.retryAfterAt()).isEqualTo(NOW.plusSeconds(120));
        server.verify();
    }

    @Test
    void unlink_withHttpDateRetryAfter_returnsAbsoluteRetryTime() {
        String retryAfter =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        ZonedDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC));
        respondWithRetryAfter(HttpStatus.SERVICE_UNAVAILABLE, "{}", retryAfter);

        KakaoAdminUnlinkResult result = client.unlink(TARGET_ID);

        assertThat(result.retryAfterAt()).isEqualTo(NOW.plusSeconds(300));
        server.verify();
    }

    @Test
    void unlink_withExcessiveRetryAfter_capsAtSixHours() {
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{}", "9999999999");

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isEqualTo(NOW.plusSeconds(6 * 60 * 60));
        server.verify();
    }

    @ParameterizedTest(name = "oversized delta-seconds case {index}")
    @MethodSource("oversizedDeltaSeconds")
    void unlink_withOversizedDeltaSeconds_ignoresHeader(String retryAfter) {
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{}", retryAfter);

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isNull();
        server.verify();
    }

    static Stream<Arguments> oversizedDeltaSeconds() {
        return Stream.of(
                Arguments.of("99999999999"),
                Arguments.of("9".repeat(1_000)),
                Arguments.of("9223372036854775808"));
    }

    @Test
    void unlink_withZeroRetryAfter_acceptsCurrentResponseTime() {
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{}", "0");

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isEqualTo(NOW);
        server.verify();
    }

    @Test
    void unlink_withPastOrMalformedRetryAfter_ignoresHeader() {
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{}", "not-a-date");

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isNull();
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("invalidRetryAfterValues")
    void unlink_withInvalidRetryAfter_ignoresHeader(String retryAfter) {
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{}", retryAfter);

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isNull();
        server.verify();
    }

    static Stream<Arguments> invalidRetryAfterValues() {
        return Stream.of(
                Arguments.of("-1"),
                Arguments.of("+1"),
                Arguments.of("1.5"),
                Arguments.of(" "),
                Arguments.of("１２"));
    }

    @Test
    void unlink_withPastHttpDate_ignoresHeader() {
        String retryAfter =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        ZonedDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{}", retryAfter);

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isNull();
        server.verify();
    }

    @Test
    void unlink_withMultipleRetryAfterValues_ignoresAmbiguousHeader() {
        server.expect(requestTo(BASE_URL + "/v1/user/unlink"))
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .header(HttpHeaders.RETRY_AFTER, "1", "2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{}"));

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isNull();
        server.verify();
    }

    @Test
    void unlink_withPermanentKnownCode_ignoresRetryAfter() {
        respondWithRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "{\"code\":-401}", "120");

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isNull();
        server.verify();
    }

    @Test
    void unlink_withRetryableKnownCodeOnFourHundred_ignoresRetryAfter() {
        respondWithRetryAfter(HttpStatus.BAD_REQUEST, "{\"code\":-1}", "120");

        assertThat(client.unlink(TARGET_ID).retryAfterAt()).isNull();
        server.verify();
    }

    @ParameterizedTest(name = "targetId {0}")
    @MethodSource("invalidTargetIds")
    void unlink_withNonPositiveTargetId_doesNotMakeHttpRequest(long targetId) {
        assertResult(
                client.unlink(targetId), KakaoAdminUnlinkDisposition.PERMANENT_REQUEST, null, null);
        server.verify();
    }

    static Stream<Arguments> invalidTargetIds() {
        return Stream.of(Arguments.of(0L), Arguments.of(-1L));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @MethodSource("bodyReadFailureStatuses")
    void unlink_whenResponseBodyReadFails_returnsRetryableWithoutRetrying(
            HttpStatus responseStatus) {
        AtomicInteger requestCount = new AtomicInteger();
        KakaoAdminApiClient bodyReadFailureClient =
                new KakaoAdminApiClient(
                        RestClient.builder()
                                .requestFactory(
                                        new BodyReadFailureRequestFactory(
                                                requestCount, responseStatus))
                                .build(),
                        new ObjectMapper(),
                        TEST_KEY,
                        CLOCK);

        assertResult(
                bodyReadFailureClient.unlink(TARGET_ID),
                KakaoAdminUnlinkDisposition.RETRYABLE,
                responseStatus.value(),
                null);
        assertThat(requestCount).hasValue(1);
    }

    static Stream<Arguments> bodyReadFailureStatuses() {
        return Stream.of(Arguments.of(HttpStatus.OK), Arguments.of(HttpStatus.BAD_REQUEST));
    }

    @Test
    void unlink_whenConnectionFails_returnsRetryable() {
        respondWithException(new ConnectException("connection refused"));

        assertTransportFailure(client.unlink(TARGET_ID));
    }

    @Test
    void unlink_whenTimeoutOccurs_returnsRetryable() {
        respondWithException(new SocketTimeoutException("read timed out"));

        assertTransportFailure(client.unlink(TARGET_ID));
    }

    @Test
    void unlink_whenDnsFails_returnsRetryable() {
        respondWithException(new UnknownHostException("unresolvable.test"));

        assertTransportFailure(client.unlink(TARGET_ID));
    }

    @Test
    void unlink_doesNotExposeSensitiveValues(CapturedOutput output) {
        String rawCanary = "raw-response-canary";
        respond(HttpStatus.BAD_REQUEST, "{\"code\":-401,\"msg\":\"" + rawCanary + "\"}");

        KakaoAdminUnlinkResult result = client.unlink(TARGET_ID);

        assertThat(result.toString()).doesNotContain(TEST_KEY, Long.toString(TARGET_ID), rawCanary);
        assertThat(output.getAll()).doesNotContain(TEST_KEY, Long.toString(TARGET_ID), rawCanary);
        server.verify();
    }

    private void respond(HttpStatus status, String body) {
        server.expect(requestTo(BASE_URL + "/v1/user/unlink"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private void respondWithException(IOException exception) {
        server.expect(requestTo(BASE_URL + "/v1/user/unlink")).andRespond(withException(exception));
    }

    @Test
    void unlink_whenFiveHundredBodyReadFails_preservesRetryAfterHeader() {
        AtomicInteger requestCount = new AtomicInteger();
        KakaoAdminApiClient bodyReadFailureClient =
                new KakaoAdminApiClient(
                        RestClient.builder()
                                .requestFactory(
                                        new BodyReadFailureRequestFactory(
                                                requestCount,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                "120"))
                                .build(),
                        new ObjectMapper(),
                        TEST_KEY,
                        CLOCK);

        assertThat(bodyReadFailureClient.unlink(TARGET_ID).retryAfterAt())
                .isEqualTo(NOW.plusSeconds(120));
        assertThat(requestCount).hasValue(1);
    }

    private void respondWithRetryAfter(HttpStatus status, String body, String retryAfter) {
        server.expect(requestTo(BASE_URL + "/v1/user/unlink"))
                .andRespond(
                        withStatus(status)
                                .header(HttpHeaders.RETRY_AFTER, retryAfter)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body));
    }

    private void assertTransportFailure(KakaoAdminUnlinkResult result) {
        assertResult(result, KakaoAdminUnlinkDisposition.RETRYABLE, null, null);
        server.verify();
    }

    private void assertResult(
            KakaoAdminUnlinkResult result,
            KakaoAdminUnlinkDisposition disposition,
            Integer httpStatus,
            Integer kakaoCode) {
        assertThat(result.disposition()).isEqualTo(disposition);
        assertThat(result.httpStatus()).isEqualTo(httpStatus);
        assertThat(result.kakaoCode()).isEqualTo(kakaoCode);
    }

    private static final class BodyReadFailureRequestFactory implements ClientHttpRequestFactory {

        private final AtomicInteger requestCount;
        private final HttpStatus responseStatus;
        private final String retryAfter;

        private BodyReadFailureRequestFactory(
                AtomicInteger requestCount, HttpStatus responseStatus) {
            this(requestCount, responseStatus, null);
        }

        private BodyReadFailureRequestFactory(
                AtomicInteger requestCount, HttpStatus responseStatus, String retryAfter) {
            this.requestCount = requestCount;
            this.responseStatus = responseStatus;
            this.retryAfter = retryAfter;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            requestCount.incrementAndGet();
            return new ClientHttpRequest() {
                private final HttpHeaders headers = new HttpHeaders();
                private final OutputStream body = new ByteArrayOutputStream();
                private final Map<String, Object> attributes = new HashMap<>();

                @Override
                public HttpMethod getMethod() {
                    return httpMethod;
                }

                @Override
                public URI getURI() {
                    return uri;
                }

                @Override
                public Map<String, Object> getAttributes() {
                    return attributes;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return headers;
                }

                @Override
                public OutputStream getBody() {
                    return body;
                }

                @Override
                public ClientHttpResponse execute() {
                    return new ClientHttpResponse() {
                        @Override
                        public HttpStatusCode getStatusCode() {
                            return responseStatus;
                        }

                        @Override
                        public String getStatusText() {
                            return responseStatus.getReasonPhrase();
                        }

                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders responseHeaders = new HttpHeaders();
                            if (retryAfter != null) {
                                responseHeaders.add(HttpHeaders.RETRY_AFTER, retryAfter);
                            }
                            return responseHeaders;
                        }

                        @Override
                        public InputStream getBody() throws IOException {
                            throw new IOException("response stream interrupted");
                        }

                        @Override
                        public void close() {}
                    };
                }
            };
        }
    }
}
