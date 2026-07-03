package com.gather.gather.domain.posting.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

/**
 * 1365 자원봉사포털 OpenAPI 공통 응답 포맷(response.header/body.items.item).
 *
 * <p>서버는 {@code type=json} 파라미터를 무시하고 항상 XML로 응답한다(실 API 호출로 확인됨,
 * 2026-07-02). XML 루트 엘리먼트가 {@code <response>}이므로 이 레코드 자체가 루트를 표현한다.
 */
@JacksonXmlRootElement(localName = "response")
public record VolunteerApiEnvelope<T>(Header header, Body<T> body) {

    public record Header(String resultCode, String resultMsg) {
        public boolean isSuccess() {
            return "00".equals(resultCode);
        }
    }

    public record Body<T>(Items<T> items, Integer numOfRows, Integer pageNo, Integer totalCount) {}

    public record Items<T>(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "item")
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<T> item) {}
}
