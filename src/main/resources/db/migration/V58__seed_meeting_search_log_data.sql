-- 모임 추천검색어 초기 부트스트랩용 시드. 검수된 키워드를 각각 10건씩 넣어
-- 실제 사용자 검색 로그와 함께 매일 오전 5시 집계에 반영되도록 한다.
INSERT INTO meeting_search_log (keyword, searched_at)
SELECT seed.keyword, CURRENT_TIMESTAMP(6)
FROM (
    SELECT '플로깅' AS keyword
    UNION ALL SELECT '러닝'
    UNION ALL SELECT '독서'
    UNION ALL SELECT '스터디'
    UNION ALL SELECT '멘토링'
    UNION ALL SELECT '문화'
    UNION ALL SELECT '환경'
    UNION ALL SELECT '유기견'
    UNION ALL SELECT '아동'
    UNION ALL SELECT '노인'
) AS seed
CROSS JOIN (
    SELECT 1 AS occurrence
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
    UNION ALL SELECT 5
    UNION ALL SELECT 6
    UNION ALL SELECT 7
    UNION ALL SELECT 8
    UNION ALL SELECT 9
    UNION ALL SELECT 10
) AS occurrences;
