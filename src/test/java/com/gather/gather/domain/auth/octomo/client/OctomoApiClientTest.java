package com.gather.gather.domain.auth.octomo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OctomoApiClientTest {

    private static final String BASE_URL = "https://api.octoverse.kr";
    private static final String API_KEY = "test-octomo-api-key";
    private static final String PNG_DATA_URL = "data:image/png;base64,iVBORw0KGgo=";

    private MockRestServiceServer server;
    private OctomoApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OctomoApiClient(builder.build(), API_KEY);
    }

    @Test
    @DisplayName("문자 조회는 OCTOMO 공식 헤더와 body를 그대로 전송한다")
    void existsMessage_sendsOfficialRequestContract() {
        server.expect(requestTo(BASE_URL + "/octomo/v1/public/message/exists"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Octomo " + API_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "mobileNum": "01012345678",
                                          "text": "GATHER-7F2K9Q8M4P",
                                          "withinMinutes": 5
                                        }
                                        """))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"exists\":true}"));

        assertThat(client.existsMessage("01012345678", "GATHER-7F2K9Q8M4P", 5)).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("QR 발급은 저장 인증문구만 body로 보내고 PNG data URL을 반환한다")
    void createQrCode_sendsTextOnly() {
        server.expect(requestTo(BASE_URL + "/octomo/v1/public/message/qr-code"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Octomo " + API_KEY))
                .andExpect(content().json("{\"text\":\"GATHER-7F2K9Q8M4P\"}"))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"qrCode\":\"" + PNG_DATA_URL + "\"}"));

        assertThat(client.createQrCode("GATHER-7F2K9Q8M4P")).isEqualTo(PNG_DATA_URL);
        server.verify();
    }

    @Test
    @DisplayName("QR 응답의 Base64 payload가 비어 있으면 서비스 불가로 변환한다")
    void createQrCode_rejectsEmptyPayload() {
        stubQrResponse("data:image/png;base64,");

        assertErrorCode(
                () -> client.createQrCode("GATHER-7F2K9Q8M4P"),
                ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("QR 응답의 payload가 유효한 Base64가 아니면 서비스 불가로 변환한다")
    void createQrCode_rejectsInvalidBase64() {
        stubQrResponse("data:image/png;base64,not-base64!");

        assertErrorCode(
                () -> client.createQrCode("GATHER-7F2K9Q8M4P"),
                ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("QR 응답의 payload가 PNG가 아니면 서비스 불가로 변환한다")
    void createQrCode_rejectsNonPngPayload() {
        stubQrResponse("data:image/png;base64,dGV4dC1kYXRh");

        assertErrorCode(
                () -> client.createQrCode("GATHER-7F2K9Q8M4P"),
                ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("401 응답은 공급자 내부 메시지를 노출하지 않고 서비스 불가로 변환한다")
    void existsMessage_mapsUnauthorizedToProviderUnavailable() {
        server.expect(requestTo(BASE_URL + "/octomo/v1/public/message/exists"))
                .andRespond(
                        withStatus(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"message\":\"Invalid API Key\"}"));

        assertErrorCode(
                () -> client.existsMessage("01012345678", "GATHER-7F2K9Q8M4P", 5),
                ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("429 응답은 휴대폰 인증 전용 요청 제한 오류로 변환한다")
    void existsMessage_mapsTooManyRequests() {
        server.expect(requestTo(BASE_URL + "/octomo/v1/public/message/exists"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertErrorCode(
                () -> client.existsMessage("01012345678", "GATHER-7F2K9Q8M4P", 5),
                ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
    }

    @Test
    @DisplayName("500 응답은 휴대폰 인증 서비스 불가로 변환한다")
    void existsMessage_mapsServerError() {
        server.expect(requestTo(BASE_URL + "/octomo/v1/public/message/exists"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertErrorCode(
                () -> client.existsMessage("01012345678", "GATHER-7F2K9Q8M4P", 5),
                ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("네트워크 타임아웃은 휴대폰 인증 서비스 불가로 변환한다")
    void existsMessage_mapsTransportFailure() {
        server.expect(requestTo(BASE_URL + "/octomo/v1/public/message/exists"))
                .andRespond(
                        request -> {
                            throw new IOException("read timed out");
                        });

        assertErrorCode(
                () -> client.existsMessage("01012345678", "GATHER-7F2K9Q8M4P", 5),
                ErrorCode.PHONE_VERIFICATION_PROVIDER_UNAVAILABLE);
    }

    private void stubQrResponse(String qrCode) {
        server.expect(requestTo(BASE_URL + "/octomo/v1/public/message/qr-code"))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"qrCode\":\"" + qrCode + "\"}"));
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
