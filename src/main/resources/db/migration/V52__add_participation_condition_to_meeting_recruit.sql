-- 모집공고(RECRUIT)에 참여 조건 입력을 추가한다(피그마 기획 대비 누락 필드, PR #152 대체).
-- 선택값이며 최대 255자로 제한한다.
ALTER TABLE meeting_recruit
    ADD COLUMN participation_condition VARCHAR(255) NULL COMMENT '참여 조건(선택, 예: 성인 및 청소년 단체 신청 가능)' AFTER external;
