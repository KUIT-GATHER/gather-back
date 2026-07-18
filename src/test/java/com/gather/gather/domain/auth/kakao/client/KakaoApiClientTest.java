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
    @DisplayName("사용자 정보 조회 네트워크 실패는 500으로 매핑한다")
    void getUserInfo_whenNetworkFailure_throwsInternalServerError() {
        apiServer
                .expect(requestTo(API_BASE_URL + "/v2/user/me"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertErrorCode(
                () -> client.getUserInfo("kakao-access-token"), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
