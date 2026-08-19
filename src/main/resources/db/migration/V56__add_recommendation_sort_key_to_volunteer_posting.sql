-- 봉사공고 추천 후보 조회(PostingRecommendationService)가 notice_end_date를
-- "NULL(상시모집)은 맨 뒤, 나머지는 오름차순"으로 정렬해야 하는데, 이 우선순위를
-- CASE 식으로 표현하면 인덱스로 정렬을 커버할 수 없어 매 페이지 filesort가 발생했다(V55 참고).
--
-- notice_end_date가 NULL이면 항상 맨 뒤로 가도록 먼 미래 날짜(9999-12-31)로 치환한
-- 생성 컬럼을 두면, 이 컬럼 하나로 "마감 지난 공고 제외" WHERE 조건과
-- "마감임박 오름차순, 상시모집은 맨 뒤" ORDER BY를 동시에 인덱스로 커버할 수 있다.
ALTER TABLE volunteer_posting
    ADD COLUMN notice_end_date_sort_key DATE
        GENERATED ALWAYS AS (COALESCE(notice_end_date, '9999-12-31')) STORED
        AFTER notice_end_date;

CREATE INDEX idx_volunteer_posting_recommend_candidates
    ON volunteer_posting (status, region_id, is_active, notice_end_date_sort_key);
