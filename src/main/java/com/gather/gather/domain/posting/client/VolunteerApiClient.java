package com.gather.gather.domain.posting.client;

import com.gather.gather.domain.posting.client.dto.VolunteerApiEnvelope;
import com.gather.gather.domain.posting.client.dto.VolunteerApiItemDto;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchCondition;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchItemDto;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 1365 자원봉사포털 OpenAPI({@code VolunteerPartcptnService}) 연동 클라이언트. */
@Component
public class VolunteerApiClient {

    private static final String DETAIL_PATH = "/getVltrPartcptnItem";
    private static final String SEARCH_PATH = "/getVltrSearchWordList";

    private final RestClient restClient;
    private final String serviceKey;

    public VolunteerApiClient(RestClient.Builder restClientBuilder, VolunteerApiProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.serviceKey = properties.serviceKey();
    }

    /** 프로그램등록번호 기준 상세조회. 결과가 없으면 예외를 던진다. */
    public VolunteerApiItemDto getItem(String progrmRegistNo) {
        VolunteerApiEnvelope<VolunteerApiItemDto> envelope =
                fetch(
                        DETAIL_PATH,
                        uriBuilder ->
                                uriBuilder
                                        .queryParam("progrmRegistNo", progrmRegistNo)
                                        .queryParam("pageNo", 1)
                                        .queryParam("numOfRows", 1)
                                        .build(),
                        new ParameterizedTypeReference<>() {});

        List<VolunteerApiItemDto> items = extractItems(envelope);
        if (items.isEmpty()) {
            throw new VolunteerApiException("조회된 봉사공고가 없습니다. progrmRegistNo=" + progrmRegistNo);
        }
        return items.get(0);
    }

    /** 검색어/지역/분야 조건으로 목록조회(페이지네이션). */
    public List<VolunteerApiSearchItemDto> searchList(
            VolunteerApiSearchCondition condition, int pageNo, int numOfRows) {
        VolunteerApiEnvelope<VolunteerApiSearchItemDto> envelope =
                fetch(
                        SEARCH_PATH,
                        uriBuilder -> {
                            uriBuilder.queryParam("pageNo", pageNo).queryParam("numOfRows", numOfRows);
                            condition.toQueryParams().forEach(uriBuilder::queryParam);
                            return uriBuilder.build();
                        },
                        new ParameterizedTypeReference<>() {});

        return extractItems(envelope);
    }

    private <T> VolunteerApiEnvelope<T> fetch(
            String path,
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>
                    uriFunction,
            ParameterizedTypeReference<VolunteerApiEnvelope<T>> responseType) {
        VolunteerApiEnvelope<T> envelope;
        try {
            envelope =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriFunction.apply(
                                                    uriBuilder
                                                            .path(path)
                                                            .queryParam("serviceKey", serviceKey)))
                            .retrieve()
                            .body(responseType);
        } catch (RestClientException e) {
            throw new VolunteerApiException("1365 API 호출에 실패했습니다. path=" + path, e);
        }
        validate(envelope, path);
        return envelope;
    }

    private <T> void validate(VolunteerApiEnvelope<T> envelope, String path) {
        if (envelope == null || envelope.header() == null) {
            throw new VolunteerApiException("1365 API 응답이 비어 있습니다. path=" + path);
        }
        VolunteerApiEnvelope.Header header = envelope.header();
        if (!header.isSuccess()) {
            throw new VolunteerApiException(
                    "1365 API 호출 실패: resultCode=" + header.resultCode() + ", resultMsg=" + header.resultMsg());
        }
    }

    private <T> List<T> extractItems(VolunteerApiEnvelope<T> envelope) {
        VolunteerApiEnvelope.Items<T> items = envelope.body().items();
        if (items == null || items.item() == null) {
            return List.of();
        }
        return items.item();
    }
}
