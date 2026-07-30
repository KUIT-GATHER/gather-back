ALTER TABLE meeting_member
    ADD COLUMN recognized_minutes INT NULL COMMENT '모임 완료 처리 이후 멤버 본인이 직접 입력하는 봉사 인정시간(분 단위)';
