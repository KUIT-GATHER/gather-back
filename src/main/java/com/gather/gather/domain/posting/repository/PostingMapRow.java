package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 봉사공고 지도 조회(#186) 전용 경량 프로젝션. {@code Posting} 엔티티 전체(특히 {@code @Lob content})를 로딩하지 않도록, 지도
 * 응답에 필요한 컬럼만 담아 Criteria {@code cb.construct()}로 직접 채운다. 인증 없이 호출 가능한 공개 API가 결과 상한 없이 무거운
 * 엔티티 전체를 조회하지 않도록 하기 위한 조치다.
 */
public record PostingMapRow(
        Long id,
        String title,
        String recruitOrg,
        String postAddress,
        Long regionId,
        PostingCategory category,
        PostingStatus status,
        LocalDate activityDate,
        LocalDate actStartDate,
        LocalDate actEndDate,
        LocalDate noticeEndDate,
        BigDecimal latitude,
        BigDecimal longitude) {}
