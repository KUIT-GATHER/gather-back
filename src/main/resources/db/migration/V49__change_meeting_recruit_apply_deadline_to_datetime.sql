-- 신청 마감일에 시각까지 포함하도록 변경한다(피그마: "2026.06.10 · 12:00 A.M." 처럼 일시 단위로 마감을 표시).
-- DATE -> DATETIME 변경 시 기존 값은 자정(00:00:00)으로 채워진다(하루 종일 신청 가능했던 기존 데이터와 동일하게 취급).
ALTER TABLE meeting_recruit
    MODIFY COLUMN apply_deadline DATETIME(6) NOT NULL COMMENT '신청 마감 일시(현재 시각~마감 일시까지 신청 가능)';
