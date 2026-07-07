package com.gather.gather.domain.posting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gather.gather.domain.posting.client.dto.VolunteerApiItemDto;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchCondition;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchItemDto;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 실제 1365 API는 {@code type=json} 파라미터를 무시하고 항상 XML로 응답한다(실 호출로 확인됨, 2026-07-02). 아래 픽스처는 {@code
 * postiong_api_spec.md}에 기록된 실제 응답 예시를 기반으로 한다.
 */
class VolunteerApiClientTest {

    private static final String BASE_URL = "http://localhost/openapi";

    private MockRestServiceServer server;
    private VolunteerApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        VolunteerApiProperties properties =
                new VolunteerApiProperties(BASE_URL, "test-service-key");
        client = new VolunteerApiClient(builder, properties);
    }

    @Test
    @DisplayName(
            "getItem parses a single <item> element (not an array) returned for progrmRegistNo")
    void getItem_returnsItem_whenApiRespondsSuccessfully() {
        String body =
                """
                <response>
                  <header>
                    <resultCode>00</resultCode>
                    <resultMsg>NORMAL SERVICE.</resultMsg>
                  </header>
                  <body>
                    <items>
                      <item>
                        <progrmRegistNo>3425935</progrmRegistNo>
                        <progrmSj>동구하랑 시민옹호인 모집</progrmSj>
                        <srvcClCode>기타</srvcClCode>
                      </item>
                    </items>
                    <numOfRows>1</numOfRows>
                    <pageNo>1</pageNo>
                    <totalCount>1</totalCount>
                  </body>
                </response>
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andExpect(queryParam("serviceKey", "test-service-key"))
                .andExpect(queryParam("progrmRegistNo", "3425935"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        VolunteerApiItemDto result = client.getItem("3425935");

        assertThat(result.progrmRegistNo()).isEqualTo("3425935");
        assertThat(result.progrmSj()).isEqualTo("동구하랑 시민옹호인 모집");
        server.verify();
    }

    @Test
    @DisplayName("getItem throws when the API reports a non-success resultCode")
    void getItem_throwsException_whenResultCodeIndicatesFailure() {
        String body =
                """
                <response>
                  <header>
                    <resultCode>30</resultCode>
                    <resultMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</resultMsg>
                  </header>
                  <body></body>
                </response>
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.getItem("999"))
                .isInstanceOf(VolunteerApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("getItem throws when no item is found for the given progrmRegistNo")
    void getItem_throwsException_whenNoItemsFound() {
        String body =
                """
                <response>
                  <header>
                    <resultCode>00</resultCode>
                    <resultMsg>NORMAL SERVICE.</resultMsg>
                  </header>
                  <body>
                    <numOfRows>0</numOfRows>
                    <pageNo>1</pageNo>
                    <totalCount>0</totalCount>
                  </body>
                </response>
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.getItem("no-such-id"))
                .isInstanceOf(VolunteerApiException.class)
                .hasMessageContaining("no-such-id");
    }

    @Test
    @DisplayName(
            "getItem retries transport/HTTP failures up to the max attempts, then wraps the last"
                    + " failure")
    void getItem_throwsException_whenHttpErrorOccurs() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withServerError());
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withServerError());
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getItem("500err"))
                .isInstanceOf(VolunteerApiException.class);
        server.verify();
    }

    @Test
    @DisplayName("getItem recovers when a transient transport failure is followed by a success")
    void getItem_recovers_whenTransientFailureFollowedBySuccess() {
        String body =
                """
                <response>
                  <header>
                    <resultCode>00</resultCode>
                    <resultMsg>NORMAL SERVICE.</resultMsg>
                  </header>
                  <body>
                    <items>
                      <item>
                        <progrmRegistNo>3425935</progrmRegistNo>
                        <progrmSj>동구하랑 시민옹호인 모집</progrmSj>
                      </item>
                    </items>
                    <numOfRows>1</numOfRows>
                    <pageNo>1</pageNo>
                    <totalCount>1</totalCount>
                  </body>
                </response>
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withServerError());
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        VolunteerApiItemDto result = client.getItem("3425935");

        assertThat(result.progrmRegistNo()).isEqualTo("3425935");
        server.verify();
    }

    @Test
    @DisplayName(
            "searchList sends condition fields as query params and parses multiple <item> siblings")
    void searchList_returnsItems_andSendsConditionQueryParams() {
        String body =
                """
                <response>
                  <header>
                    <resultCode>00</resultCode>
                    <resultMsg>NORMAL SERVICE.</resultMsg>
                  </header>
                  <body>
                    <items>
                      <item>
                        <progrmRegistNo>1</progrmRegistNo>
                        <progrmSj>a</progrmSj>
                      </item>
                      <item>
                        <progrmRegistNo>2</progrmRegistNo>
                        <progrmSj>b</progrmSj>
                      </item>
                    </items>
                    <numOfRows>10</numOfRows>
                    <pageNo>1</pageNo>
                    <totalCount>2</totalCount>
                  </body>
                </response>
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrSearchWordList")))
                .andExpect(queryParam("pageNo", "1"))
                .andExpect(queryParam("numOfRows", "10"))
                .andExpect(queryParam("keyword", "volunteer"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        "volunteer",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        List<VolunteerApiSearchItemDto> result = client.searchList(condition, 1, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).progrmRegistNo()).isEqualTo("1");
        assertThat(result.get(1).progrmRegistNo()).isEqualTo("2");
        server.verify();
    }

    @Test
    @DisplayName("searchList returns an empty list when no results are found")
    void searchList_returnsEmptyList_whenNoResults() {
        String body =
                """
                <response>
                  <header>
                    <resultCode>00</resultCode>
                    <resultMsg>NORMAL SERVICE.</resultMsg>
                  </header>
                  <body>
                    <numOfRows>10</numOfRows>
                    <pageNo>1</pageNo>
                    <totalCount>0</totalCount>
                  </body>
                </response>
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrSearchWordList")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null);

        assertThat(client.searchList(condition, 1, 10)).isEmpty();
    }
}
