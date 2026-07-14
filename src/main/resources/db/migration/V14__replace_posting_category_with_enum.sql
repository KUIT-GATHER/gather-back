-- 주의: 아래 CASE 매핑은 PostingSyncService.CATEGORY_MAPPING과 값이 동일해야 한다
-- (과거 데이터 백필 vs 신규 동기화가 서로 다르게 분류되는 것을 방지). 한쪽을 고치면 반드시 다른 쪽도 함께 고칠 것.
ALTER TABLE volunteer_posting
    ADD COLUMN category VARCHAR(20) NULL COMMENT 'PostingCategory enum (ENVIRONMENT/EDUCATION/CULTURE/COMMUNITY/WELFARE/OVERSEAS)';

UPDATE volunteer_posting v
    JOIN categories c ON v.category_id = c.id
    SET v.category = CASE c.name
        WHEN '생활편의' THEN 'WELFARE'
        WHEN '주거환경' THEN 'ENVIRONMENT'
        WHEN '상담·멘토링' THEN 'WELFARE'
        WHEN '교육' THEN 'EDUCATION'
        WHEN '보건·의료' THEN 'WELFARE'
        WHEN '농어촌 봉사' THEN 'COMMUNITY'
        WHEN '문화·체육·예술·관광' THEN 'CULTURE'
        WHEN '환경·생태계보호' THEN 'ENVIRONMENT'
        WHEN '사무행정' THEN 'WELFARE'
        WHEN '지역안전·보호' THEN 'COMMUNITY'
        WHEN '인권·공익' THEN 'WELFARE'
        WHEN '재난·재해' THEN 'COMMUNITY'
        WHEN '국제협력·해외봉사' THEN 'OVERSEAS'
        WHEN '기타' THEN 'COMMUNITY'
        WHEN '자원봉사 기본교육' THEN 'EDUCATION'
        WHEN '온라인자원봉사' THEN 'COMMUNITY'
        ELSE 'COMMUNITY'
    END;

UPDATE volunteer_posting SET category = 'COMMUNITY' WHERE category IS NULL;

ALTER TABLE volunteer_posting
    MODIFY COLUMN category VARCHAR(20) NOT NULL,
    DROP COLUMN category_id;
