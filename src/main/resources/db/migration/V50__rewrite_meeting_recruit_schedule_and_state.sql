-- 모집공고 필드 개편: 활동 기간(activityStartAt~activityEndAt)으로 통합하고 지역/확정 상태를 추가한다.
-- 주의: act_date(DATE)->activity_start_at(DATETIME) 전환 시 시각은 자정(00:00)으로 채워진다.
-- 기존 act_start_time/act_end_time 값을 보존해야 한다면, 이 마이그레이션 적용 전 별도 백필 스크립트로
-- activity_start_at/activity_end_at을 미리 채워 넣는 것을 권장한다(운영 데이터가 있는 경우).
ALTER TABLE meeting_recruit
    ADD COLUMN region_id BIGINT NULL COMMENT 'region 테이블 참조. 도메인 결합을 피해 FK 없이 ID만 보관' AFTER post_id,
    CHANGE COLUMN act_date activity_start_at DATETIME NOT NULL COMMENT '활동 시작 일시',
    ADD COLUMN activity_end_at DATETIME NOT NULL COMMENT '활동 종료 일시' AFTER activity_start_at,
    DROP COLUMN act_start_time,
    DROP COLUMN act_end_time,
    CHANGE COLUMN apply_deadline apply_deadline_at DATETIME NOT NULL COMMENT '신청 마감 일시(이 시각까지 신청 가능)',
    CHANGE COLUMN is_external external BIT(1) NOT NULL DEFAULT 0 COMMENT '외부 공고 공개 여부',
    ADD COLUMN confirmation_status VARCHAR(20) NOT NULL DEFAULT 'UNCONFIRMED' COMMENT 'UNCONFIRMED/CONFIRMED' AFTER external,
    ADD COLUMN confirmed_at DATETIME NULL AFTER confirmation_status;

-- 신청 당시 사용자 구분(MEMBER/EXTERNAL)을 참여 기록에 저장한다.
ALTER TABLE meeting_recruit_participation
    ADD COLUMN applicant_type VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'MEMBER/EXTERNAL' AFTER user_id;
