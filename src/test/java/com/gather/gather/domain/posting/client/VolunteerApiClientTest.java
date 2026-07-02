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

class VolunteerApiClientTest {

    private static final String BASE_URL = "http://localhost/openapi";

    private MockRestServiceServer server;
    private VolunteerApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        VolunteerApiProperties properties = new VolunteerApiProperties(BASE_URL, "test-service-key");
        client = new VolunteerApiClient(builder, properties);
    }

    @Test
    @DisplayName("getItem parses a single item object (not array) returned for progrmRegistNo")
    void getItem_returnsItem_whenApiRespondsSuccessfully() {
        String body =
                """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": {"progrmRegistNo": "3425935", "progrmSj": "동구하랑 시민옹호인 모집", "srvcClCode": "기타"}},
                      "numOfRows": 1, "pageNo": 1, "totalCount": 1
                    }
                  }
                }
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andExpect(queryParam("serviceKey", "test-service-key"))
                .andExpect(queryParam("type", "json"))
                .andExpect(queryParam("progrmRegistNo", "3425935"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

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
                {"response": {"header": {"resultCode": "30", "resultMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}, "body": {}}}
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getItem("999"))
                .isInstanceOf(VolunteerApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("getItem throws when no item is found for the given progrmRegistNo")
    void getItem_throwsException_whenNoItemsFound() {
        String body =
                """
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."}, "body": {"items": null, "numOfRows": 0, "pageNo": 1, "totalCount": 0}}}
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getItem("no-such-id"))
                .isInstanceOf(VolunteerApiException.class)
                .hasMessageContaining("no-such-id");
    }

    @Test
    @DisplayName("getItem wraps transport/HTTP failures in VolunteerApiException")
    void getItem_throwsException_whenHttpErrorOccurs() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrPartcptnItem")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getItem("500err")).isInstanceOf(VolunteerApiException.class);
    }

    @Test
    @DisplayName("searchList sends condition fields as query params and parses multiple items")
    void searchList_returnsItems_andSendsConditionQueryParams() {
        String body =
                """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": [
                        {"progrmRegistNo": "1", "progrmSj": "a"},
                        {"progrmRegistNo": "2", "progrmSj": "b"}
                      ]},
                      "numOfRows": 10, "pageNo": 1, "totalCount": 2
                    }
                  }
                }
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrSearchWordList")))
                .andExpect(queryParam("pageNo", "1"))
                .andExpect(queryParam("numOfRows", "10"))
                .andExpect(queryParam("keyword", "volunteer"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        "volunteer", null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null);

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
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."}, "body": {"items": null, "numOfRows": 10, "pageNo": 1, "totalCount": 0}}}
                """;

        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/getVltrSearchWordList")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null);

        assertThat(client.searchList(condition, 1, 10)).isEmpty();
    }
}
