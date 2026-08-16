package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingLocation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.QueryUtils;

@RequiredArgsConstructor
public class PostingRepositoryImpl implements PostingRepositoryCustom {

    private static final List<PostingStatus> DEFAULT_STATUSES =
            List.of(PostingStatus.RECRUITING, PostingStatus.CLOSED);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 지도 조회(#186)는 인증 없이 호출 가능한 공개 API라 bounds를 아주 넓게 잡으면 매칭되는 공고 수가 무제한으로 커질 수 있다. 서버 측 최대 결과 수를
     * 두어, 넓은 bounds 요청이 와도 응답 크기와 메모리 사용량이 일정 수준 이상으로 커지지 않도록 한다.
     */
    private static final int MAX_MAP_RESULTS = 1000;

    private final EntityManager entityManager;

    @Override
    public Page<Posting> search(
            PostingStatus status,
            List<Long> regionIds,
            LocalDate noticeStartFrom,
            LocalDate noticeEndTo,
            String keyword,
            PostingCategory category,
            Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Posting> query = cb.createQuery(Posting.class);
        Root<Posting> root = query.from(Posting.class);
        query.where(
                buildPredicates(
                                cb,
                                root,
                                status,
                                regionIds,
                                noticeStartFrom,
                                noticeEndTo,
                                keyword,
                                category)
                        .toArray(new Predicate[0]));
        query.orderBy(buildOrders(cb, root, status, pageable));

        TypedQuery<Posting> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Posting> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Posting> countRoot = countQuery.from(Posting.class);
        countQuery
                .select(cb.count(countRoot))
                .where(
                        buildPredicates(
                                        cb,
                                        countRoot,
                                        status,
                                        regionIds,
                                        noticeStartFrom,
                                        noticeEndTo,
                                        keyword,
                                        category)
                                .toArray(new Predicate[0]));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<Posting> root,
            PostingStatus status,
            List<Long> regionIds,
            LocalDate noticeStartFrom,
            LocalDate noticeEndTo,
            String keyword,
            PostingCategory category) {
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(
                status != null
                        ? cb.equal(root.get("status"), status)
                        : root.get("status").in(DEFAULT_STATUSES));

        if (regionIds != null) {
            predicates.add(
                    regionIds.isEmpty() ? cb.disjunction() : root.get("regionId").in(regionIds));
        }

        if (noticeStartFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("noticeStartDate"), noticeStartFrom));
        }

        if (noticeEndTo != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("noticeEndDate"), noticeEndTo));
        }

        if (keyword != null) {
            String pattern = "%" + keyword + "%";
            predicates.add(
                    cb.or(
                            cb.like(root.get("title"), pattern),
                            cb.like(root.get("recruitOrg"), pattern)));
        }

        if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
        }

        return predicates;
    }

    /**
     * status가 지정되지 않은 조회에서만 모집중(RECRUITING) 그룹을 마감(CLOSED) 그룹보다 앞세운다. 단, 외부 공공데이터 API 동기화 지연으로
     * noticeEndDate가 이미 지났는데도 status가 아직 RECRUITING으로 남아있는 공고는 실제로는 신청 불가능하므로 CLOSED 그룹과 함께 뒤로
     * 보낸다(마감임박 정렬 시 이미 마감된 공고가 최상단에 노출되는 문제 방지). 마감임박(noticeEndDate 오름차순) 정렬이 요청된 경우에는 마감일이 없는 상시모집
     * 공고가 DB의 NULL 정렬 정책에 따라 마감 임박 공고보다 먼저 노출될 수 있으므로, 마감일 유무 우선순위를 추가로 적용해 "마감일이 있고 아직 안 지남 → 마감일
     * 없음 → 마감(지남)" 순서를 보장한다.
     */
    private List<Order> buildOrders(
            CriteriaBuilder cb, Root<Posting> root, PostingStatus status, Pageable pageable) {
        List<Order> orders = new ArrayList<>();
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        if (status == null) {
            Predicate isOpenRecruiting =
                    cb.and(
                            cb.equal(root.get("status"), PostingStatus.RECRUITING),
                            isNoticeStillOpen(cb, root, today));
            orders.add(cb.asc(cb.<Integer>selectCase().when(isOpenRecruiting, 0).otherwise(1)));
        } else if (status == PostingStatus.RECRUITING) {
            orders.add(
                    cb.asc(
                            cb.<Integer>selectCase()
                                    .when(isNoticeStillOpen(cb, root, today), 0)
                                    .otherwise(1)));
        }

        if (isNoticeEndDateAscendingRequested(pageable.getSort())) {
            orders.add(cb.asc(nullNoticeEndDatePriority(cb, root)));
        }

        orders.addAll(QueryUtils.toOrders(pageable.getSort(), root, cb));

        boolean containsIdSort =
                pageable.getSort().stream().anyMatch(order -> order.getProperty().equals("id"));
        if (!containsIdSort) {
            orders.add(cb.desc(root.get("id")));
        }

        return orders;
    }

    private boolean isNoticeEndDateAscendingRequested(Sort sort) {
        return sort.stream()
                .anyMatch(
                        order ->
                                order.getProperty().equals("noticeEndDate") && order.isAscending());
    }

    /** noticeEndDate가 없는 공고(상시 모집 등)를 마감일이 있는 공고보다 뒤로 보낸다. */
    private Expression<Integer> nullNoticeEndDatePriority(CriteriaBuilder cb, Root<Posting> root) {
        return cb.<Integer>selectCase().when(cb.isNull(root.get("noticeEndDate")), 1).otherwise(0);
    }

    /** noticeEndDate가 없는 공고(상시 모집 등)는 통과시키고, 있으면 오늘을 포함해 아직 지나지 않은 경우만 통과시킨다. */
    private Predicate isNoticeStillOpen(CriteriaBuilder cb, Root<Posting> root, LocalDate today) {
        return cb.or(
                cb.isNull(root.get("noticeEndDate")),
                cb.greaterThanOrEqualTo(root.get("noticeEndDate"), today));
    }

    @Override
    public List<PostingMapRow> searchForMap(
            List<Long> regionIds,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            PostingCategory category,
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<PostingMapRow> query = cb.createQuery(PostingMapRow.class);
        Root<Posting> root = query.from(Posting.class);

        // Posting 엔티티 전체(특히 @Lob content)를 로딩하지 않도록, 지도 응답에 필요한 컬럼만 프로젝션한다.
        query.select(
                cb.construct(
                        PostingMapRow.class,
                        root.get("id"),
                        root.get("title"),
                        root.get("recruitOrg"),
                        root.get("postAddress"),
                        root.get("regionId"),
                        root.get("category"),
                        root.get("status"),
                        root.get("activityDate"),
                        root.get("actStartDate"),
                        root.get("actEndDate"),
                        root.get("noticeEndDate"),
                        root.get("latitude"),
                        root.get("longitude")));
        query.where(
                buildMapPredicates(
                                cb,
                                query,
                                root,
                                regionIds,
                                activityStartDate,
                                activityEndDate,
                                category,
                                swLat,
                                swLng,
                                neLat,
                                neLng)
                        .toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("id")));

        return entityManager.createQuery(query).setMaxResults(MAX_MAP_RESULTS).getResultList();
    }

    private List<Predicate> buildMapPredicates(
            CriteriaBuilder cb,
            CriteriaQuery<PostingMapRow> query,
            Root<Posting> root,
            List<Long> regionIds,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            PostingCategory category,
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(root.get("status").in(DEFAULT_STATUSES));

        if (regionIds != null) {
            predicates.add(
                    regionIds.isEmpty() ? cb.disjunction() : root.get("regionId").in(regionIds));
        }
        if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
        }

        // 활동일 겹침(overlap): 종료일 = act_end_date, 없으면 act_start_date, 그마저 없으면 activity_date로
        // 대체. 시작일 = act_start_date, 없으면 activity_date로 대체(GET /postings의 overlap 필터와 동일 규칙).
        Expression<LocalDate> effectiveStartDate =
                cb.<LocalDate>coalesce(root.get("actStartDate"), root.get("activityDate"));
        Expression<LocalDate> effectiveEndDate =
                cb.<LocalDate>coalesce(root.get("actEndDate"), effectiveStartDate);
        if (activityStartDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(effectiveEndDate, activityStartDate));
        }
        if (activityEndDate != null) {
            predicates.add(cb.lessThanOrEqualTo(effectiveStartDate, activityEndDate));
        }

        predicates.add(buildBoundsPredicate(cb, query, root, swLat, swLng, neLat, neLng));

        return predicates;
    }

    /** 1번째 장소(Posting 자신의 위·경도) 또는 2·3번째 장소(PostingLocation) 중 하나라도 지도 bounds 안에 있으면 통과시킨다. */
    private Predicate buildBoundsPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<PostingMapRow> query,
            Root<Posting> root,
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng) {
        Predicate primaryInBounds =
                cb.and(
                        cb.isNotNull(root.get("latitude")),
                        cb.isNotNull(root.get("longitude")),
                        cb.between(root.get("latitude"), swLat, neLat),
                        cb.between(root.get("longitude"), swLng, neLng));

        Subquery<Long> locationSubquery = query.subquery(Long.class);
        Root<PostingLocation> locationRoot = locationSubquery.from(PostingLocation.class);
        locationSubquery
                .select(locationRoot.get("id"))
                .where(
                        cb.equal(locationRoot.get("postingId"), root.get("id")),
                        cb.isNotNull(locationRoot.get("latitude")),
                        cb.isNotNull(locationRoot.get("longitude")),
                        cb.between(locationRoot.get("latitude"), swLat, neLat),
                        cb.between(locationRoot.get("longitude"), swLng, neLng));

        return cb.or(primaryInBounds, cb.exists(locationSubquery));
    }
}
