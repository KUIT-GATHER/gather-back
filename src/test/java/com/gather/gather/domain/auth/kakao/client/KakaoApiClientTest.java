package com.gather.gather.domain.auth.kakao.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.dto.KakaoUserResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class KakaoApiClientTest {

    private static final String AUTH_BASE_URL = "http://localhost/kauth";
    private static final String API_BASE_URL = "http://localhost/kapi";
    private static final String REDIRECT_URI = "https://gathernow.kr/login/kakao/callback";
    private static final String ADMIN_KEY = "test-kakao-admin-key-0123456789a";
    private static final String APP_ID = "1234567";
    private static final Long PROVIDER_USER_ID = 4242L;
    private static final String SIGNUP_TOKEN_SECRET =
            "z9tOf6reUdkTRI0KFFiydLKdxpayBBxVWSAm7EJTgKXolFCFvnQ4qViBrdh6y7yP";

    private MockRestServiceServer authServer;
    private MockRestServiceServer apiServer;
    private KakaoApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder authBuilder = RestClient.builder().baseUrl(AUTH_BASE_URL);
        RestClient.Builder apiBuilder = RestClient.builder().baseUrl(API_BASE_URL);
        authServer = MockRestServiceServer.bindTo(authBuilder).build();
        apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        client = new KakaoApiClient(authBuilder.build(), apiBuilder.build(), properties());
    }

    private KakaoProperties properties() {
        return new KakaoProperties(
                "test-rest-api-key",
                "test-client-secret",
                ADMIN_KEY,
                APP_ID,
                List.of(REDIRECT_URI),
                SIGNUP_TOKEN_SECRET,
                900,
                AUTH_BASE_URL,
                API_BASE_URL);
    }

    @Test
    @DisplayName("토큰 교환 요청은 client_secret을 포함한다 (누락 시 카카오가 KOE010으로 거부)")
    void requestAccessToken_sendsClientSecret() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", "test-rest-api-key");
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", "auth-code");
        expectedForm.add("client_secret", "test-client-secret");

        authServer
                .expect(requestTo(AUTH_BASE_URL + "/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(expectedForm))
                .andRespond(
                        withSuccess(
                                """
                                {"access_token":"kakao-access-token","token_type":"bearer","expires_in":21599}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(client.requestAccessToken("auth-code", REDIRECT_URI))
                .isEqualTo("kakao-access-token");
        authServer.verify();
    }

    @Test
    @DisplayName("인가 코드 오류(4xx)는 500이 아니라 400으로 매핑한다")
    void requestAccessToken_whenKakaoRejectsWith4xx_throwsValidationError() {
        authServer
                .expect(requestTo(AUTH_BASE_URL + "/oauth/token"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"error":"invalid_grant","error_code":"KOE320","error_description":"authorization code not found"}
                                        """));

        assertErrorCode(
                () -> client.requestAccessToken("reused-code", REDIRECT_URI),
                ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("토큰 교환 429는 사용자 입력 오류가 아니라 카카오 서비스 제한으로 매핑한다")
    void requestAccessToken_whenRateLimited_throwsKakaoApiUnavailable() {
        authServer
                .expect(requestTo(AUTH_BASE_URL + "/oauth/token"))
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":\"too_many_requests\"}"));

        assertErrorCode(
                () -> client.requestAccessToken("auth-code", REDIRECT_URI),
                ErrorCode.KAKAO_API_UNAVAILABLE);
        authServer.verify();
    }

    @Test
    @DisplayName("토큰 교환 5xx는 500으로 매핑한다")
    void requestAccessToken_whenKakaoRespondsWith5xx_throwsInternalServerError() {
        authServer.expect(requestTo(AUTH_BASE_URL + "/oauth/token")).andRespond(withServerError());

        assertErrorCode(
                () -> client.requestAccessToken("auth-code", REDIRECT_URI),
                ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("토큰 교환 타임아웃은 500으로 매핑한다")
    void requestAccessToken_whenTimeout_throwsInternalServerError() {
        authServer
                .expect(requestTo(AUTH_BASE_URL + "/oauth/token"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertErrorCode(
                () -> client.requestAccessToken("auth-code", REDIRECT_URI),
                ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("토큰 교환 응답에 access_token이 없으면 500으로 처리한다")
    void requestAccessToken_whenAccessTokenMissing_throwsInternalServerError() {
        authServer
                .expect(requestTo(AUTH_BASE_URL + "/oauth/token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertErrorCode(
                () -> client.requestAccessToken("auth-code", REDIRECT_URI),
                ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("사용자 정보 조회는 카카오 Access Token을 Bearer로 보내고 회원번호와 닉네임을 반환한다")
    void getUserInfo_returnsIdAndNickname() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                .andRespond(
                        withSuccess(
                                """
                                {"id":123456789,"connected_at":"2026-07-17T00:00:00Z","kakao_account":{"profile":{"nickname":"동현"}}}
                                """,
                                MediaType.APPLICATION_JSON));

        KakaoUserResponse response = client.getUserInfo("kakao-access-token");

        assertThat(response.id()).isEqualTo(123456789L);
        assertThat(response.nickname()).isEqualTo("동현");
        apiServer.verify();
    }

    @Test
    @DisplayName("닉네임이 없는 사용자 정보 응답은 nickname이 null이다")
    void getUserInfo_whenNicknameMissing_returnsNullNickname() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v2/user/me"))
                .andRespond(
                        withSuccess(
                                """
                        {"id":123456789}
                        """,
                                MediaType.APPLICATION_JSON));

        KakaoUserResponse response = client.getUserInfo("kakao-access-token");

        assertThat(response.id()).isEqualTo(123456789L);
        assertThat(response.nickname()).isNull();
        apiServer.verify();
    }

    @Test
    @DisplayName("사용자 정보 응답에 회원번호가 없으면 500으로 처리한다")
    void getUserInfo_whenIdMissing_throwsInternalServerError() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v2/user/me"))
                .andRespond(
                        withSuccess(
                                """
                                {"kakao_account":{"profile":{"nickname":"동현"}}}
                                """,
                                MediaType.APPLICATION_JSON));

        assertErrorCode(
                () -> client.getUserInfo("kakao-access-token"), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("사용자 정보 조회 401은 400으로 매핑한다")
    void getUserInfo_whenUnauthorized_throwsValidationError() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v2/user/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"code\":-401}"));

        assertErrorCode(() -> client.getUserInfo("expired-token"), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("사용자 정보 조회 429는 사용자 입력 오류가 아니라 카카오 서비스 제한으로 매핑한다")
    void getUserInfo_whenRateLimited_throwsKakaoApiUnavailable() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v2/user/me"))
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"msg\":\"too many requests\"}"));

        assertErrorCode(
                () -> client.getUserInfo("kakao-access-token"), ErrorCode.KAKAO_API_UNAVAILABLE);
        apiServer.verify();
    }

    @Test
    @DisplayName("사용자 정보 조회 네트워크 실패는 500으로 매핑한다")
    void getUserInfo_whenNetworkFailure_throwsInternalServerError() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v2/user/me"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertErrorCode(
                () -> client.getUserInfo("kakao-access-token"), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("연결 해제는 어드민 키와 회원번호를 보내고 성공을 돌려준다")
    void unlink_sendsAdminKeyAndTargetId() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("target_id_type", "user_id");
        expectedForm.add("target_id", String.valueOf(PROVIDER_USER_ID));

        apiServer
                .expect(requestTo(API_BASE_URL + "/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY))
                .andExpect(content().formData(expectedForm))
                .andRespond(
                        withSuccess(
                                "{\"id\":" + PROVIDER_USER_ID + "}", MediaType.APPLICATION_JSON));

        assertThat(client.unlink(PROVIDER_USER_ID)).isEqualTo(KakaoUnlinkResult.SUCCESS);
        apiServer.verify();
    }

    @Test
    @DisplayName("연결 해제 4xx는 재시도해도 소용없으므로 영구 실패로 분류한다")
    void unlink_when4xx_returnsPermanentFailure() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v1/user/unlink"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":-101}"));

        assertThat(client.unlink(PROVIDER_USER_ID)).isEqualTo(KakaoUnlinkResult.PERMANENT_FAILURE);
    }

    @Test
    @DisplayName("연결 해제 5xx는 카카오 장애이므로 재시도 대상으로 분류한다")
    void unlink_when5xx_returnsTransientFailure() {
        apiServer.expect(requestTo(API_BASE_URL + "/v1/user/unlink")).andRespond(withServerError());

        assertThat(client.unlink(PROVIDER_USER_ID)).isEqualTo(KakaoUnlinkResult.TRANSIENT_FAILURE);
    }

    @Test
    @DisplayName("연결 해제 429는 4xx지만 시간이 지나면 풀리므로 재시도 대상이다")
    void unlink_whenRateLimited_returnsTransientFailure() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v1/user/unlink"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client.unlink(PROVIDER_USER_ID)).isEqualTo(KakaoUnlinkResult.TRANSIENT_FAILURE);
    }

    @Test
    @DisplayName("연결 해제 네트워크 실패는 예외 대신 재시도 대상으로 돌려준다")
    void unlink_whenNetworkFailure_returnsTransientFailure() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v1/user/unlink"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(client.unlink(PROVIDER_USER_ID)).isEqualTo(KakaoUnlinkResult.TRANSIENT_FAILURE);
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
