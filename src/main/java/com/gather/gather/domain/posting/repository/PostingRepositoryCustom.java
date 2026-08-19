package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.math.BigDecimal;
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
     * 달리 이 필터·정렬 조합은 V67의 {@code notice_end_date_sort_key} 생성 컬럼과 전용 복합 인덱스({@code (status,
     * is_active, notice_end_date_sort_key, region_id)})로 커버되어, regionIds 유무와 무관하게 filesort 없이 처리된다.
     *
     * <p>추천은 상위 5건만 필요하지만 전체 후보를 순회하며 채점해야 해서(더 높은 점수의 후보가 뒤 페이지에 있을 수 있음) 총 건수는 쓰지 않는다. 따라서 페이지마다
     * 별도 COUNT 쿼리가 나가는 {@link Page} 대신, content만 한 번에 조회하고 다음 페이지 존재 여부만 판단하는 {@link Slice}를 반환해
     * 페이지당 쿼리 수를 절반으로 줄인다.
     *
     * <p><b>{@code pageable}의 {@link Pageable#getSort()}는 무시된다</b> — 정렬은 항상 위에서 설명한 마감임박 오름차순(id
     * 내림차순 tiebreak)으로 고정되며, offset/limit(page/size)만 반영한다. 다른 정렬이 필요하면 이 메서드가 아니라 {@link #search}를
     * 사용할 것.
     *
     * <p>{@code regionIds}가 {@code null}이면 지역 필터 없이 전체 지역을 대상으로 하고, 빈 리스트({@code List.of()})면 매칭되는
     * 지역이 없다는 의미로 결과가 항상 0건이다({@link #search}의 regionIds 파라미터와 동일한 규약).
     */
    Slice<Posting> searchRecommendationCandidates(
            List<Long> regionIds, LocalDate today, Pageable pageable);

    /**
     * 봉사공고 지도 조회(#186)용. RECRUITING·CLOSED 상태만 대상이며, 활동일 겹침(overlap) 필터·지역·카테고리와 함께 지도 bounds(1번째
     * 장소 또는 2·3번째 장소 중 하나라도 bounds 안이면 포함) 조건으로 조회한다. 페이지네이션은 없지만 결과 수 상한({@code
     * PostingRepositoryImpl#MAX_MAP_RESULTS})이 있고, {@code Posting} 엔티티 전체가 아니라 지도 응답에 필요한 컬럼만 담은 경량
     * 프로젝션({@link PostingMapRow})을 반환한다(인증 없이 호출 가능한 공개 API라 @Lob content 등 불필요한 컬럼을 로딩하지 않기 위함).
     */
    List<PostingMapRow> searchForMap(
            List<Long> regionIds,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            PostingCategory category,
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng);
}
