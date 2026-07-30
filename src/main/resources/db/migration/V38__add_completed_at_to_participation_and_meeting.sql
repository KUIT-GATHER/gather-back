ALTER TABLE posting_participation
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT '개인 봉사 완료 처리 시점(뱃지 판정용, updatedAt과 별개)';

ALTER TABLE meeting
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT '모임 봉사 완료 처리 시점(뱃지 판정용, updatedAt과 별개)';
