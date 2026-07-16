-- 주의: 아래 CASE 매핑은 V14__replace_posting_category_with_enum.sql 및
-- PostingSyncService.CATEGORY_MAPPING과 값이 동일해야 한다. 한쪽을 고치면 반드시 다른 쪽도 함께 고칠 것.
ALTER TABLE user_interest_category
    ADD COLUMN category VARCHAR(20) NULL COMMENT 'PostingCategory enum (ENVIRONMENT/EDUCATION/CULTURE/COMMUNITY/WELFARE/OVERSEAS)';

UPDATE user_interest_category uic
    JOIN categories c ON uic.category_id = c.id
    SET uic.category = CASE c.name
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

DELETE uic_duplicate
FROM user_interest_category uic_duplicate
    JOIN user_interest_category uic_retained
        ON uic_duplicate.user_id = uic_retained.user_id
        AND uic_duplicate.category = uic_retained.category
        AND uic_duplicate.category_id > uic_retained.category_id;

ALTER TABLE user_interest_category
    DROP FOREIGN KEY fk_user_interest_category_category;

ALTER TABLE user_interest_category
    MODIFY COLUMN category VARCHAR(20) NOT NULL,
    DROP PRIMARY KEY,
    DROP COLUMN category_id,
    ADD PRIMARY KEY (user_id, category);
