-- 재발송 쿨다운과 일일 리셋 판정은 기존 created_at(마지막 발송 시각)을 재사용하므로 컬럼을 따로 추가하지 않는다.
-- 기존 행과 롤링 배포 중 구버전 앱이 만드는 행은 이미 1회 이상 발송된 상태이므로 기본값을 1로 둔다.
ALTER TABLE email_verification
    ADD COLUMN daily_send_count INT NOT NULL DEFAULT 1 COMMENT '당일 발송 횟수. created_at 날짜가 바뀌면 리셋한다(최대 5회).',
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 COMMENT '현재 코드에 대한 인증 실패 시도 횟수. 재발송 시 리셋한다(최대 5회).',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '발송 실패 보상과 상태 변경의 충돌을 감지하는 낙관적 잠금 버전';
