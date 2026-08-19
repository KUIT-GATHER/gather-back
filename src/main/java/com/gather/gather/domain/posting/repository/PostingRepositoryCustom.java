package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

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
     * 봉사공고 추천({@code PostingRecommendationService}) 전용 후보 조회. status=RECRUITING, is_active=true,
     * 마감일이 아직 지나지 않은(또는 상시모집인) 공고만 WHERE에서 걸러내고 마감임박 오름차순(상시모집은 맨 뒤)으로 정렬한다 — 범용 {@link #search}와
     * 달리 이 필터·정렬 조합은 V56의 {@code notice_end_date_sort_key} 생성 컬럼과 전용 복합 인덱스로 커버되어 filesort 없이 처리된다.
     *
     * <p>추천은 상위 5건만 필요하지만 전체 후보를 순회하며 채점해야 해서(더 높은 점수의 후보가 뒤 페이지에 있을 수 있음) 총 건수는 쓰지 않는다. 따라서 페이지마다
     * 별도 COUNT 쿼리가 나가는 {@link Page} 대신, content만 한 번에 조회하고 다음 페이지 존재 여부만 판단하는 {@link Slice}를 반환해
     * 페이지당 쿼리 수를 절반으로 줄인다.
     */
    Slice<Posting> searchRecommendationCandidates(
            List<Long> regionIds, LocalDate today, Pageable pageable);
}
