ALTER TABLE posting_participation
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT '개인 봉사 완료 처리 시점(뱃지 판정용, updatedAt과 별개)';

ALTER TABLE meeting
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT '모임 봉사 완료 처리 시점(뱃지 판정용, updatedAt과 별개)';

-- 이 컬럼 추가 이전부터 COMPLETED/REVIEWED 상태였던 기존 행은 completed_at이 NULL로 남아
-- 뱃지 판정 시 NPE를 일으킨다. 실제 완료 시점을 알 수 없으므로 updated_at을 근사값으로 채운다.
UPDATE posting_participation
    SET completed_at = updated_at
    WHERE status IN ('COMPLETED', 'REVIEWED')
      AND completed_at IS NULL;

UPDATE meeting
    SET completed_at = updated_at
    WHERE status = 'COMPLETED'
      AND completed_at IS NULL;
