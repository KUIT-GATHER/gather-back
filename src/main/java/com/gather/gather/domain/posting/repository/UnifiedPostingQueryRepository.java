package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * 앱 전체 봉사공고 목록(#9)을 위한 통합 조회. 기존 봉사공고(volunteer_posting)와 external=true인 모임 모집공고를 하나의 페이지네이션·정렬 안에서
 * UNION ALL로 합쳐 반환한다(JPQL/Criteria로는 서로 다른 엔티티를 union할 수 없어 네이티브 SQL을 사용).
 *
 * <p>알려진 단순화(후속 과제)
 *
 * <ul>
 *   <li>noticeStartDate/noticeEndDate 필터는 기존 봉사공고 쪽에만 적용된다(모집공고에는 대응 개념이 없음).
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class UnifiedPostingQueryRepository {

    /** PostingListItem 정렬 허용 필드 -> 실제 컬럼명. 화이트리스트 밖 값은 ORDER BY에 직접 넣지 않는다(SQL 인젝션 방지). */
    private static final Map<String, String> SORT_COLUMNS =
            Map.of(
                    "id", "id",
                    "createdAt", "created_at",
                    "activityStartAt", "activity_start_at",
                    "applyDeadlineAt", "apply_deadline_at");

    private static final List<String> ACTIVE_PARTICIPATION_STATUSES =
            List.of("APPLIED", "CONFIRMED", "COMPLETED", "REVIEWED");

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final EntityManager entityManager;

    public boolean isSortable(String property) {
        return SORT_COLUMNS.containsKey(property);
    }

    public record SearchResult(List<UnifiedPostingRow> rows, long totalElements) {}

    public SearchResult search(
            PostingStatus status,
            List<Long> regionIds,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String keyword,
            PostingCategory category,
            Pageable pageable) {

        Map<String, Object> params = new java.util.HashMap<>();
        String postingWhere =
                buildPostingWhere(
                        status,
                        regionIds,
                        noticeStartDate,
                        noticeEndDate,
                        keyword,
                        category,
                        params);
        String recruitWhere = buildRecruitWhere(status, regionIds, keyword, category, params);
        Map<String, Object> orderParams = new java.util.HashMap<>();
        String orderBy = buildOrderBy(status, pageable.getSort(), orderParams);

        String unionSql =
                POSTING_SELECT.replace("__WHERE__", postingWhere)
                        + " UNION ALL "
                        + RECRUIT_SELECT.replace("__WHERE__", recruitWhere);

        String pageSql =
                "SELECT * FROM ("
                        + unionSql
                        + ") unified "
                        + orderBy
                        + " LIMIT :limit OFFSET :offset";
        Query pageQuery = entityManager.createNativeQuery(pageSql);
        bindParams(pageQuery, params);
        bindParams(pageQuery, orderParams);
        pageQuery.setParameter("limit", pageable.getPageSize());
        pageQuery.setParameter("offset", (int) pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rawRows = pageQuery.getResultList();
        List<UnifiedPostingRow> rows = rawRows.stream().map(this::toRow).toList();

        String countSql = "SELECT COUNT(*) FROM (" + unionSql + ") unified";
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindParams(countQuery, params);
        Number total = (Number) countQuery.getSingleResult();

        return new SearchResult(rows, total.longValue());
    }

    private static final String POSTING_SELECT =
            "SELECT 'POSTING' AS source_type, p.id AS id, NULL AS meeting_id, p.title AS title, "
                    + "p.recruit_org AS organization_name, p.region_id AS region_id, p.act_place AS place, "
                    + "CASE WHEN p.act_start_date IS NOT NULL THEN CAST(p.act_start_date AS DATETIME) "
                    + "ELSE CAST(p.activity_date AS DATETIME) END AS activity_start_at, "
                    + "CASE WHEN p.act_end_date IS NOT NULL THEN CAST(p.act_end_date AS DATETIME) "
                    + "ELSE CAST(COALESCE(p.act_start_date, p.activity_date) AS DATETIME) END AS activity_end_at, "
                    + "CASE WHEN p.notice_end_date IS NOT NULL THEN CAST(p.notice_end_date AS DATETIME) ELSE NULL END AS apply_deadline_at, "
                    + "p.recruit_count AS max_participants, p.applicant_count AS applied_count, "
                    + "CAST(JSON_ARRAY(p.category) AS CHAR) AS categories_json, "
                    + "CAST(p.status AS CHAR) AS status, p.created_at AS created_at "
                    + "FROM volunteer_posting p WHERE __WHERE__";

    private static final String RECRUIT_SELECT =
            "SELECT 'MEETING_RECRUIT' AS source_type, post.id AS id, m.id AS meeting_id, post.title AS title, "
                    + "m.name AS organization_name, r.region_id AS region_id, r.place AS place, "
                    + "r.activity_start_at AS activity_start_at, r.activity_end_at AS activity_end_at, "
                    + "r.apply_deadline_at AS apply_deadline_at, r.max_participants AS max_participants, "
                    + "(SELECT COUNT(*) FROM meeting_recruit_participation prt WHERE prt.post_id = post.id "
                    + "AND prt.status IN ('APPLIED','CONFIRMED','COMPLETED','REVIEWED')) AS applied_count, "
                    + "CAST((SELECT JSON_ARRAYAGG(mrc.category) FROM meeting_recruit_category mrc "
                    + "WHERE mrc.recruit_id = r.id) AS CHAR) AS categories_json, "
                    + "CASE WHEN r.confirmation_status = 'CONFIRMED' THEN 'CLOSED' "
                    + "WHEN r.apply_deadline_at < NOW() THEN 'CLOSED' "
                    + "WHEN (SELECT COUNT(*) FROM meeting_recruit_participation prt2 WHERE prt2.post_id = post.id "
                    + "AND prt2.status IN ('APPLIED','CONFIRMED','COMPLETED','REVIEWED')) >= r.max_participants THEN 'CLOSED' "
                    + "ELSE 'RECRUITING' END AS status, "
                    + "post.created_at AS created_at "
                    + "FROM meeting_recruit r JOIN post ON post.id = r.post_id JOIN meeting m ON m.id = post.meeting_id "
                    + "WHERE __WHERE__";

    private String buildPostingWhere(
            PostingStatus status,
            List<Long> regionIds,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String keyword,
            PostingCategory category,
            Map<String, Object> params) {
        StringBuilder where = new StringBuilder("1=1");
        if (status != null) {
            where.append(" AND p.status = :status");
            params.put("status", status.name());
        } else {
            where.append(" AND p.status IN ('RECRUITING','CLOSED')");
        }
        if (regionIds != null) {
            if (regionIds.isEmpty()) {
                where.append(" AND 1=0");
            } else {
                where.append(" AND p.region_id IN (:regionIds)");
                params.put("regionIds", regionIds);
            }
        }
        if (noticeStartDate != null) {
            where.append(" AND p.notice_start_date >= :noticeStartDate");
            params.put("noticeStartDate", noticeStartDate);
        }
        if (noticeEndDate != null) {
            where.append(" AND p.notice_end_date <= :noticeEndDate");
            params.put("noticeEndDate", noticeEndDate);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.title LIKE :keyword OR p.recruit_org LIKE :keyword)");
            params.put("keyword", "%" + keyword + "%");
        }
        if (category != null) {
            where.append(" AND p.category = :category");
            params.put("category", category.name());
        }
        return where.toString();
    }

    private String buildRecruitWhere(
            PostingStatus status,
            List<Long> regionIds,
            String keyword,
            PostingCategory category,
            Map<String, Object> params) {
        StringBuilder where =
                new StringBuilder(
                        "r.external = 1 AND post.deleted_at IS NULL AND m.deleted_at IS NULL");
        if (status != null) {
            // COMPLETED 상태는 모집공고 쪽에 대응 개념이 없어 항상 결과가 비게 된다(기존 목록과 동일하게 기본은 RECRUITING+CLOSED만 노출).
            where.append(
                    " AND (CASE WHEN r.confirmation_status = 'CONFIRMED' THEN 'CLOSED' "
                            + "WHEN r.apply_deadline_at < NOW() THEN 'CLOSED' "
                            + "WHEN (SELECT COUNT(*) FROM meeting_recruit_participation prt3 WHERE prt3.post_id = post.id "
                            + "AND prt3.status IN ('APPLIED','CONFIRMED','COMPLETED','REVIEWED')) >= r.max_participants THEN 'CLOSED' "
                            + "ELSE 'RECRUITING' END) = :status");
            params.put("status", status.name());
        }
        if (regionIds != null) {
            if (regionIds.isEmpty()) {
                where.append(" AND 1=0");
            } else {
                where.append(" AND r.region_id IN (:regionIds)");
                params.put("regionIds", regionIds);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (post.title LIKE :keyword OR m.name LIKE :keyword)");
            params.put("keyword", "%" + keyword + "%");
        }
        if (category != null) {
            where.append(
                    " AND EXISTS (SELECT 1 FROM meeting_recruit_category mrc2 WHERE mrc2.recruit_id = r.id AND mrc2.category = :category)");
            params.put("category", category.name());
        }
        return where.toString();
    }

    /**
     * status가 null 또는 RECRUITING이고 apply_deadline_at 오름차순(마감임박) 정렬이 요청된 경우에만 우선순위 보정을 앞세운다. 기존
     * 봉사공고는 외부 공공데이터 API 동기화 지연으로 마감일이 지났는데도 status가 아직 RECRUITING으로 남아있을 수 있으므로(모집공고 쪽은 status 자체가
     * apply_deadline_at 기준으로 계산되어 나오므로 해당 없음), 실제 신청 가능한 공고(RECRUITING이고 마감일이 없거나 아직 지나지 않음)를 먼저
     * 내려보낸 뒤, 그 안에서는 마감일이 있는 공고를 마감일 없는 상시모집 공고보다 앞세워 D-day → D-1 → D-2 순서를 보장한다. "오늘" 판정은 DB 서버의
     * 타임존(NOW())이 아니라 Java에서 Asia/Seoul 기준으로 계산해 파라미터로 바인딩한다(CI 등 DB 서버가 UTC일 때 자정 경계에서 하루 어긋나는 것을
     * 방지).
     */
    private String buildOrderBy(PostingStatus status, Sort sort, Map<String, Object> orderParams) {
        StringBuilder orderBy = new StringBuilder("ORDER BY ");
        boolean any = false;

        if (isApplyDeadlineAscendingRequested(sort)
                && (status == null || status == PostingStatus.RECRUITING)) {
            orderBy.append(
                    "CASE WHEN status = 'RECRUITING' AND (apply_deadline_at IS NULL OR "
                            + "DATE(apply_deadline_at) >= :todayForOrder) THEN 0 ELSE 1 END ASC, "
                            + "CASE WHEN apply_deadline_at IS NULL THEN 1 ELSE 0 END ASC");
            orderParams.put("todayForOrder", LocalDate.now(SEOUL_ZONE));
            any = true;
        }

        boolean hasIdOrder = false;
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                continue;
            }
            if (any) {
                orderBy.append(", ");
            }
            orderBy.append(column).append(order.isAscending() ? " ASC" : " DESC");
            if (order.getProperty().equals("id")) {
                hasIdOrder = true;
            }
            any = true;
        }
        if (!any) {
            orderBy.append("created_at DESC");
            any = true;
        }
        if (!hasIdOrder) {
            orderBy.append(", id DESC");
        }
        return orderBy.toString();
    }

    private boolean isApplyDeadlineAscendingRequested(Sort sort) {
        return sort.stream()
                .anyMatch(
                        order ->
                                order.getProperty().equals("applyDeadlineAt")
                                        && order.isAscending());
    }

    private void bindParams(Query query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    private UnifiedPostingRow toRow(Object[] row) {
        return new UnifiedPostingRow(
                (String) row[0],
                toLong(row[1]),
                toLong(row[2]),
                (String) row[3],
                (String) row[4],
                toLong(row[5]),
                (String) row[6],
                toLocalDateTime(row[7]),
                toLocalDateTime(row[8]),
                toLocalDateTime(row[9]),
                toInteger(row[10]),
                toInteger(row[11]),
                (String) row[12],
                (String) row[13]);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigInteger bi) {
            return bi.longValue();
        }
        return ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).intValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay();
        }
        throw new IllegalStateException("Unsupported datetime type: " + value.getClass());
    }
}
