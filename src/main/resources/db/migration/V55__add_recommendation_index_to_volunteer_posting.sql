-- volunteer_posting에는 status/region_id/notice_end_date에 인덱스가 전혀 없어(PK, ext_id unique만 존재),
-- 봉사공고 추천/목록 조회가 매 요청마다 status='RECRUITING' 조건으로 풀스캔을 한다.
-- 정렬(ORDER BY)은 CASE 식(마감임박 우선순위 등)을 포함해 이 인덱스로 커버되지 않아 filesort는 여전히 발생하지만,
-- WHERE 절의 status(+region_id) 필터링은 이 인덱스의 리프 스캔으로 좁혀지므로 풀스캔 대비 스캔 행 수가 크게 줄어든다.
CREATE INDEX idx_volunteer_posting_status_region_notice_end
    ON volunteer_posting (status, region_id, notice_end_date);
