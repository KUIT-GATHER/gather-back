package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.QueryUtils;

@RequiredArgsConstructor
public class PostingRepositoryImpl implements PostingRepositoryCustom {

    private static final List<PostingStatus> DEFAULT_STATUSES =
            List.of(PostingStatus.RECRUITING, PostingStatus.CLOSED);

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
     * 보낸다(마감임박 정렬 시 이미 마감된 공고가 최상단에 노출되는 문제 방지).
     */
    private List<Order> buildOrders(
            CriteriaBuilder cb, Root<Posting> root, PostingStatus status, Pageable pageable) {
        List<Order> orders = new ArrayList<>();
        LocalDate today = LocalDate.now();
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
        orders.addAll(QueryUtils.toOrders(pageable.getSort(), root, cb));

        boolean containsIdSort =
                pageable.getSort().stream().anyMatch(order -> order.getProperty().equals("id"));
        if (!containsIdSort) {
            orders.add(cb.desc(root.get("id")));
        }

        return orders;
    }

    /** noticeEndDate가 없는 공고(상시 모집 등)는 통과시키고, 있으면 오늘을 포함해 아직 지나지 않은 경우만 통과시킨다. */
    private Predicate isNoticeStillOpen(CriteriaBuilder cb, Root<Posting> root, LocalDate today) {
        return cb.or(
                cb.isNull(root.get("noticeEndDate")),
                cb.greaterThanOrEqualTo(root.get("noticeEndDate"), today));
    }
}
