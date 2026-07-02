package com.gather.gather.domain.posting.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/** 1365 자원봉사포털 OpenAPI 공통 응답 포맷(response.header/body.items.item). */
public record VolunteerApiEnvelope<T>(Response<T> response) {

    public record Response<T>(Header header, Body<T> body) {}

    public record Header(String resultCode, String resultMsg) {
        public boolean isSuccess() {
            return "00".equals(resultCode);
        }
    }

    public record Body<T>(Items<T> items, Integer numOfRows, Integer pageNo, Integer totalCount) {}

    public record Items<T>(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<T> item) {}
}
