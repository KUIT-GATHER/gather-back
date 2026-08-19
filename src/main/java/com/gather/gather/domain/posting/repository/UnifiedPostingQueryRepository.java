package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * 앱 전체 봉사공고 목록(#9)을 위한 통합 조회. 기존 봉사공고(volunteer_posting)와 external=true인 모임 모집공고를 하나의 정렬 안에서 UNION
 * ALL로 합쳐 반환한다(JPQL/Criteria로는 서로 다른 엔티티를 union할 수 없어 네이티브 SQL을 사용).
 *
 * <p>무한스크롤 최적화를 위해 OFFSET/COUNT 기반 페이지네이션 대신 키셋(커서) 페이지네이션을 쓴다. 정렬 키(우선순위 버킷 포함)를 등장 순서대로 튜플 비교하는
 * 조건 {@code (k1 > c1) OR (k1 = c1 AND k2 > c2) OR ...}으로 다음 페이지를 찾으므로, 뒤 페이지로 갈수록 느려지지 않고 목록 조회 도중
 * 데이터가 추가·삭제돼도 항목이 중복되거나 누락되지 않는다. 총 개수(COUNT)는 무한스크롤에 필요 없으므로 계산하지 않고, 대신 요청한 size보다 1개 더 가져와 다음
 * 페이지 존재 여부(hasNext)만 판단한다.
 *
 * <p>알려진 단순화(후속 과제)
 *
 * <ul>
 *   <li>noticeStartDate/noticeEndDate 필터는 기존 봉사공고 쪽에만 적용된다(모집공고에는 대응 개념이 없음).
 *   <li>커서는 발급 당시의 정렬 파라미터(sort, status 등 우선순위 버킷에 영향을 주는 조건)에 종속적이다. 정렬을 바꾼 채로 같은 커서를 이어붙이면 키 개수가
 *       달라져 400(VALIDATION_ERROR)이 반환된다 — 클라이언트는 스크롤 세션 동안 sort/status 등 정렬에 영향을 주는 파라미터를 고정해야 한다.
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
                    "applyDeadlineAt", "apply_deadline_at",
                    "appliedCount", "applied_count");

    /** 커서 토큰을 SQL 바인드 파라미터로 되돌릴 때 필요한 타입 정보. */
    private static final Map<String, ValueType> SORT_VALUE_TYPES =
            Map.of(
                    "id", ValueType.LONG,
                    "createdAt", ValueType.DATETIME,
                    "activityStartAt", ValueType.DATETIME,
                    "applyDeadlineAt", ValueType.DATETIME,
                    "appliedCount", ValueType.INTEGER);

    private static final List<String> ACTIVE_PARTICIPATION_STATUSES =
            List.of("APPLIED", "CONFIRMED", "COMPLETED", "REVIEWED");

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    /** UNION 결과의 기본 선택 컬럼 개수(source_type ~ created_at). 커서용 추가 컬럼은 이 뒤에 ok_0, ok_1... 로 덧붙는다. */
    private static final int BASE_COLUMN_COUNT = 15;

    private final EntityManager entityManager;

    public boolean isSortable(String property) {
        return SORT_COLUMNS.containsKey(property);
    }

    private enum ValueType {
        LONG,
        INTEGER,
        DATETIME
    }

    /** 정렬 우선순위 한 단계. selectExpr은 UNION 결과 컬럼 기준 SQL 표현식(우선순위 버킷은 CASE 식, 그 외는 컬럼명 그대로). */
    private record OrderKey(String selectExpr, boolean ascending, ValueType type) {}

    public record CursorSearchResult(
            List<UnifiedPostingRow> rows, String nextCursor, boolean hasNext) {}

    public CursorSearchResult search(
            PostingStatus status,
            List<Long> regionIds,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            String keyword,
            PostingCategory category,
            Sort sort,
            String cursor,
            int size) {

        Map<String, Object> params = new HashMap<>();
        String postingWhere =
                buildPostingWhere(
                        status,
                        regionIds,
                        noticeStartDate,
                        noticeEndDate,
                        activityStartDate,
                        activityEndDate,
                        keyword,
                        category,
                        params);
        String recruitWhere =
                buildRecruitWhere(
                        status,
                        regionIds,
                        activityStartDate,
                        activityEndDate,
                        keyword,
                        category,
                        params);

        Map<String, Object> orderParams = new HashMap<>();
        List<OrderKey> orderKeys = buildOrderKeys(status, sort, orderParams);

        String unionSql =
                POSTING_SELECT.replace("__WHERE__", postingWhere)
                        + " UNION ALL "
                        + RECRUIT_SELECT.replace("__WHERE__", recruitWhere);

        StringBuilder extraSelect = new StringBuilder();
        for (int i = 0; i < orderKeys.size(); i++) {
            extraSelect
                    .append(", ")
                    .append(orderKeys.get(i).selectExpr())
                    .append(" AS ok_")
                    .append(i);
        }

        StringBuilder sql =
                new StringBuilder("SELECT unified.*")
                        .append(extraSelect)
                        .append(" FROM (")
                        .append(unionSql)
                        .append(") unified");

        Map<String, Object> keysetParams = new HashMap<>();
        if (cursor != null && !cursor.isBlank()) {
            List<String> tokens = decodeCursor(cursor, orderKeys.size());
            sql.append(" WHERE ").append(buildKeysetWhere(orderKeys, tokens, keysetParams));
        }

        sql.append(' ').append(buildOrderBySql(orderKeys)).append(" LIMIT :limit");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);
        bindParams(query, orderParams);
        bindParams(query, keysetParams);
        // 다음 페이지 존재 여부를 별도 COUNT 쿼리 없이 판단하기 위해 요청 size보다 1개 더 가져온다.
        query.setParameter("limit", size + 1);

        @SuppressWarnings("unchecked")
        List<Object[]> rawRows = query.getResultList();

        boolean hasNext = rawRows.size() > size;
        List<Object[]> pageRows = hasNext ? rawRows.subList(0, size) : rawRows;

        List<UnifiedPostingRow> rows = pageRows.stream().map(this::toRow).toList();
        String nextCursor =
                hasNext && !pageRows.isEmpty()
                        ? encodeCursor(orderKeys, pageRows.get(pageRows.size() - 1))
                        : null;

        return new CursorSearchResult(rows, nextCursor, hasNext);
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
            LocalDate activityStartDate,
            LocalDate activityEndDate,
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
        if (activityStartDate != null) {
            where.append(
                    " AND COALESCE(p.act_end_date, p.act_start_date, p.activity_date) >= :activityStartDate");
            params.put("activityStartDate", activityStartDate);
        }
        if (activityEndDate != null) {
            where.append(" AND COALESCE(p.act_start_date, p.activity_date) <= :activityEndDate");
            params.put("activityEndDate", activityEndDate);
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
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            String keyword,
            PostingCategory category,
            Map<String, Object> params) {
        StringBuilder where =
                new StringBuilder(
                        "r.external = 1 AND post.deleted_at IS NULL AND m.deleted_at IS NULL");
        if (activityStartDate != null) {
            where.append(" AND DATE(r.activity_end_at) >= :activityStartDate");
            params.put("activityStartDate", activityStartDate);
        }
        if (activityEndDate != null) {
            where.append(" AND DATE(r.activity_start_at) <= :activityEndDate");
            params.put("activityEndDate", activityEndDate);
        }
        if (status != null) {
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
     * 정렬 우선순위를 튜플로 표현한 키 목록을 만든다. {@link #buildOrderBySql}과 {@link #buildKeysetWhere}가 이 목록을 그대로
     * 재사용하므로, ORDER BY와 키셋 WHERE의 우선순위가 항상 일치한다(둘이 어긋나면 페이지가 중복/누락될 수 있어 반드시 같은 소스에서 파생돼야 한다).
     *
     * <p>apply_deadline_at 오름차순(마감임박) 정렬이 요청되면, 마감일 없는 상시모집 공고를 마감일 있는 공고보다 뒤로 미루는 우선순위 보정을 status
     * 필터와 무관하게 항상 앞세운다(레거시 {@code PostingRepositoryImpl}과 동일한 계약).
     *
     * <p>추가로 status가 null 또는 RECRUITING인 경우에는, 외부 공공데이터 API 동기화 지연으로 마감일이 지났는데도 status가 아직
     * RECRUITING으로 남아있을 수 있는 기존 봉사공고(volunteer_posting)를 실제 신청 가능한 공고 뒤로 미루는 보정을 추가한다. 이 재판정은
     * {@code source_type <> 'POSTING'}으로 volunteer_posting 행에만 적용한다 — 모집공고(meeting_recruit) 행은
     * status 자체가 이미 DB 세션 타임존(UTC) 기준 {@code NOW()}로 계산되어 나오는데, 이 재판정은 Java에서 Asia/Seoul 기준으로 계산한
     * 날짜를 쓰기 때문에 두 시계를 하나의 술어에 섞으면 매일 KST 00:00~09:00 구간에서 status=RECRUITING인 모집공고가 마감 그룹 뒤로 잘못 밀리는
     * 문제가 있었다. "오늘" 판정은 DB 서버의 타임존이 아니라 Java에서 Asia/Seoul 기준으로 계산해 파라미터로 바인딩한다(CI 등 DB 서버가 UTC일 때
     * 자정 경계에서 하루 어긋나는 것을 방지).
     */
    private List<OrderKey> buildOrderKeys(
            PostingStatus status, Sort sort, Map<String, Object> orderParams) {
        List<OrderKey> keys = new ArrayList<>();
        boolean applyDeadlineAscending = isApplyDeadlineAscendingRequested(sort);

        if (applyDeadlineAscending && (status == null || status == PostingStatus.RECRUITING)) {
            keys.add(
                    new OrderKey(
                            "CASE WHEN status = 'RECRUITING' AND (source_type <> 'POSTING' "
                                    + "OR apply_deadline_at IS NULL OR DATE(apply_deadline_at) >= :todayForOrder) "
                                    + "THEN 0 ELSE 1 END",
                            true,
                            ValueType.INTEGER));
            orderParams.put("todayForOrder", LocalDate.now(SEOUL_ZONE));
        }

        if (applyDeadlineAscending) {
            keys.add(
                    new OrderKey(
                            "CASE WHEN apply_deadline_at IS NULL THEN 1 ELSE 0 END",
                            true,
                            ValueType.INTEGER));
        }

        boolean hasIdOrder = false;
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                continue;
            }
            keys.add(
                    new OrderKey(
                            column,
                            order.isAscending(),
                            SORT_VALUE_TYPES.get(order.getProperty())));
            if (order.getProperty().equals("id")) {
                hasIdOrder = true;
            }
        }

        if (keys.isEmpty()) {
            keys.add(new OrderKey("created_at", false, ValueType.DATETIME));
        }
        if (!hasIdOrder) {
            // id는 항상 마지막 타이브레이커: 동률인 값이 있어도 커서가 유일하게 "다음 행"을 가리킬 수 있게 한다.
            keys.add(new OrderKey("id", false, ValueType.LONG));
        }
        return keys;
    }

    private String buildOrderBySql(List<OrderKey> keys) {
        StringBuilder sb = new StringBuilder("ORDER BY ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("ok_").append(i).append(keys.get(i).ascending() ? " ASC" : " DESC");
        }
        return sb.toString();
    }

    /**
     * 키셋 페이지네이션 조건: {@code (k0 > c0) OR (k0 <=> c0 AND k1 > c1) OR (k0 <=> c0 AND k1 <=> c1 AND k2
     * > c2) OR ...} 형태로, 정렬 우선순위를 앞에서부터 튜플 비교한다. 앞선 키들의 동률 판정에는 MySQL의 null-safe 동등 비교(<=>)를 써서,
     * apply_deadline_at처럼 null이 가능한 키에서 "커서 값도 null, 현재 행도 null"인 경우를 올바르게 동률로 취급하고 다음 키 비교로 넘어가게
     * 한다(일반 {@code =}는 NULL과 비교하면 항상 UNKNOWN이라 이 목적에 쓸 수 없다).
     */
    private String buildKeysetWhere(
            List<OrderKey> keys, List<String> tokens, Map<String, Object> keysetParams) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append('(');
            for (int j = 0; j < i; j++) {
                sb.append(keys.get(j).selectExpr()).append(" <=> :k").append(j).append(" AND ");
            }
            OrderKey key = keys.get(i);
            sb.append(key.selectExpr()).append(key.ascending() ? " > :k" : " < :k").append(i);
            sb.append(')');
        }
        sb.append(')');

        for (int i = 0; i < keys.size(); i++) {
            keysetParams.put("k" + i, decodeToken(tokens.get(i), keys.get(i).type()));
        }
        return sb.toString();
    }

    private List<String> decodeCursor(String cursor, int expectedSize) {
        List<String> tokens;
        try {
            tokens = PostingCursor.decode(cursor);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (tokens.size() != expectedSize) {
            // 정렬 파라미터가 커서 발급 당시와 달라져 키 개수가 안 맞는 경우(예: sort를 바꿔서 이어붙이기 시도).
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return tokens;
    }

    private String encodeCursor(List<OrderKey> keys, Object[] lastRow) {
        List<String> tokens = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            tokens.add(encodeToken(lastRow[BASE_COLUMN_COUNT + i], keys.get(i).type()));
        }
        return PostingCursor.encode(tokens);
    }

    private String encodeToken(Object value, ValueType type) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case LONG -> String.valueOf(toLong(value));
            case INTEGER -> String.valueOf(toInteger(value));
            case DATETIME -> toLocalDateTime(value).toString();
        };
    }

    private Object decodeToken(String token, ValueType type) {
        if (token == null) {
            return null;
        }
        try {
            return switch (type) {
                case LONG -> Long.valueOf(token);
                case INTEGER -> Integer.valueOf(token);
                case DATETIME -> LocalDateTime.parse(token);
            };
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
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
