-- 봉사공고 추천 후보 조회(PostingRecommendationService)가 notice_end_date를
-- "NULL(상시모집)은 맨 뒤, 나머지는 오름차순"으로 정렬해야 하는데, 이 우선순위를
-- CASE 식으로 표현하면 인덱스로 정렬을 커버할 수 없어 매 페이지 filesort가 발생했다(V55 참고).
--
-- notice_end_date가 NULL이면 항상 맨 뒤로 가도록 먼 미래 날짜(9999-12-31)로 치환한
-- 생성 컬럼을 두면, 이 컬럼 하나로 "마감 지난 공고 제외" WHERE 조건과
-- "마감임박 오름차순, 상시모집은 맨 뒤" ORDER BY를 동시에 인덱스로 커버할 수 있다.
--
-- 참고: STORED 생성 컬럼 추가는 MySQL 8.0의 INSTANT ADD COLUMN 최적화 대상이 아니라
-- 테이블 전체를 재작성하는 INPLACE 방식으로 처리된다(동시 DML은 허용되지만 데이터 볼륨에
-- 비례해 시간이 걸린다). 운영 반영 전 유사 규모 데이터로 소요 시간을 확인할 것.
ALTER TABLE volunteer_posting
    ADD COLUMN notice_end_date_sort_key DATE
        GENERATED ALWAYS AS (COALESCE(notice_end_date, '9999-12-31')) STORED
        AFTER notice_end_date;

-- region_id는 활동지역 미설정 회원·비로그인(전체 트래픽의 상당수) 조회에서는 WHERE 절에
-- 아예 등장하지 않는다(PostingRepositoryImpl#buildRecommendationPredicates). B-Tree
-- 복합 인덱스는 leftmost-prefix 규칙을 따르므로, region_id를 status/is_active와
-- notice_end_date_sort_key(정렬 기준 컬럼) 사이에 두면 region_id 조건이 없는 조회에서
-- notice_end_date_sort_key가 인덱스 정렬 순서로 이어지지 못해 filesort가 재발한다.
-- region_id를 정렬 기준 컬럼 뒤로 옮기면 region_id 필터가 없는(흔한) 경로에서도 정렬이
-- 인덱스로 커버되고, region_id 필터가 있는 경로는 Index Condition Pushdown으로 걸러진다.
CREATE INDEX idx_volunteer_posting_recommend_candidates
    ON volunteer_posting (status, is_active, notice_end_date_sort_key, region_id);
