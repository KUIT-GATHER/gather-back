package com.gather.gather.domain.posting.client.dto;

/**
 * 1365 봉사참여정보상세조회({@code getVltrPartcptnItem}) 응답 item 매핑.
 *
 * <p>외부 API 원본 필드를 그대로 보존하며, 엔티티 매핑/타입 변환은 동기화 서비스에서 처리한다.
 */
public record VolunteerApiItemDto(
        String actBeginTm,
        String actEndTm,
        String actPlace,
        String actWkdy,
        String adultPosblAt,
        String appTotal,
        String areaAddress1,
        String areaAddress2,
        String areaAddress3,
        String areaLalo1,
        String areaLalo2,
        String areaLalo3,
        String email,
        String familyPosblAt,
        String fxnum,
        String grpPosblAt,
        String gugunCd,
        String mnnstNm,
        String nanmmbyNm,
        String nanmmbyNmAdmn,
        String noticeBgnde,
        String noticeEndde,
        String pbsvntPosblAt,
        String postAdres,
        String progrmBgnde,
        String progrmCn,
        String progrmEndde,
        String progrmRegistNo,
        String progrmSj,
        String progrmSttusSe,
        String rcritNmpr,
        String sidoCd,
        String srvcClCode,
        String telno,
        String yngbgsPosblAt) {}
