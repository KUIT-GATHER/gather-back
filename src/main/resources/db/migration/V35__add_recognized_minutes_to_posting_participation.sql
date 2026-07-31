ALTER TABLE posting_participation
    ADD COLUMN recognized_minutes INT NULL COMMENT '완료 처리 이후 사용자가 직접 입력하는 봉사 인정시간(분 단위)';
