-- 팀장용 신청자 관리(출석 처리)에 필요한 컬럼 추가.
ALTER TABLE meeting_recruit_participation
    ADD COLUMN attendance_status VARCHAR(20) NOT NULL DEFAULT 'UNSET' COMMENT 'UNSET/PRESENT/ABSENT' AFTER status,
    ADD COLUMN recognized_minutes_applied INT NOT NULL DEFAULT 0 COMMENT '출석 처리로 실제 반영된 인정 시간(분)' AFTER attendance_status;
