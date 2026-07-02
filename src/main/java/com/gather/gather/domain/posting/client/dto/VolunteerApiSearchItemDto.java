package com.gather.gather.domain.posting.client.dto;

/**
 * 1365 검색어봉사참여정보목록조회({@code getVltrSearchWordList}) 응답 item 매핑.
 *
 * <p>지역코드(sidoCd/gugunCd)·분야명(srvcClCode)을 포함해 목록 단계에서 region/category 매칭에 쓰인다.
 */
public record VolunteerApiSearchItemDto(
        String actBeginTm,
        String actEndTm,
        String actPlace,
        String adultPosblAt,
        String gugunCd,
        String nanmmbyNm,
        String noticeBgnde,
        String noticeEndde,
        String progrmBgnde,
        String progrmEndde,
        String progrmRegistNo,
        String progrmSj,
        String progrmSttusSe,
        String sidoCd,
        String srvcClCode,
        String url,
        String yngbgsPosblAt) {}
