package com.gather.gather.domain.posting.client.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code getVltrSearchWordList} 요청 조건. 값이 없는 필드는 쿼리파라미터에서 제외한다. */
public record VolunteerApiSearchCondition(
        String keyword,
        String schCateGu,
        String schSido,
        String schSign1,
        String upperClCode,
        String nanmClCode,
        String progrmBgnde,
        String progrmEndde,
        String noticeBgnde,
        String noticeEndde,
        String actBeginTm,
        String actEndTm,
        String actPlace,
        String nanmmbyNm,
        String adultPosblAt,
        String yngbgsPosblAt) {

    public Map<String, String> toQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "keyword", keyword);
        putIfPresent(params, "schCateGu", schCateGu);
        putIfPresent(params, "schSido", schSido);
        putIfPresent(params, "schSign1", schSign1);
        putIfPresent(params, "upperClCode", upperClCode);
        putIfPresent(params, "nanmClCode", nanmClCode);
        putIfPresent(params, "progrmBgnde", progrmBgnde);
        putIfPresent(params, "progrmEndde", progrmEndde);
        putIfPresent(params, "noticeBgnde", noticeBgnde);
        putIfPresent(params, "noticeEndde", noticeEndde);
        putIfPresent(params, "actBeginTm", actBeginTm);
        putIfPresent(params, "actEndTm", actEndTm);
        putIfPresent(params, "actPlace", actPlace);
        putIfPresent(params, "nanmmbyNm", nanmmbyNm);
        putIfPresent(params, "adultPosblAt", adultPosblAt);
        putIfPresent(params, "yngbgsPosblAt", yngbgsPosblAt);
        return params;
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }
}
