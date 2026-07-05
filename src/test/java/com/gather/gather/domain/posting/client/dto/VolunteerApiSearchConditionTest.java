package com.gather.gather.domain.posting.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VolunteerApiSearchConditionTest {

    @Test
    @DisplayName("toQueryParams excludes null and blank fields")
    void toQueryParams_excludesNullAndBlankFields() {
        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        "봉사",
                        null,
                        "6110000",
                        "",
                        null,
                        null,
                        "20260101",
                        "20260131",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Y",
                        null);

        Map<String, String> params = condition.toQueryParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("keyword", "봉사"),
                        Map.entry("schSido", "6110000"),
                        Map.entry("progrmBgnde", "20260101"),
                        Map.entry("progrmEndde", "20260131"),
                        Map.entry("adultPosblAt", "Y"));
    }

    @Test
    @DisplayName("toQueryParams returns empty map when every field is null")
    void toQueryParams_returnsEmptyMap_whenAllFieldsNull() {
        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null);

        assertThat(condition.toQueryParams()).isEmpty();
    }
}
