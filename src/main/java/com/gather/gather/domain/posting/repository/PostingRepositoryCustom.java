package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostingRepositoryCustom {

    /**
     * status가 null이면 RECRUITING과 CLOSED를 모두 조회하되, RECRUITING을 우선 정렬하고 그 안에서 pageable의 정렬을 적용한다.
     * status가 주어지면 해당 상태만 조회한다(우선순위 정렬은 의미가 없어 생략).
     */
    Page<Posting> search(
            PostingStatus status,
            List<Long> regionIds,
            LocalDate noticeStartFrom,
            LocalDate noticeEndTo,
            String keyword,
            PostingCategory category,
            Pageable pageable);

    /**
     * 봉사공고 지도 조회(#186)용. RECRUITING·CLOSED 상태만 대상이며, 활동일 겹침(overlap) 필터·지역·카테고리와 함께 지도 bounds(1번째
     * 장소 또는 2·3번째 장소 중 하나라도 bounds 안이면 포함) 조건으로 조회한다. 페이지네이션 없이 전체 목록을 반환한다.
     */
    List<Posting> searchForMap(
            List<Long> regionIds,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            PostingCategory category,
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng);
}
