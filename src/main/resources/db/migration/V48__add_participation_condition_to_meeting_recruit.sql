-- 모집공고(RECRUIT)에 참여 조건 입력을 추가한다(피그마 기획 대비 누락 필드).
-- 모임 자체의 participation_condition(V3, meeting 테이블)과 별개로,
-- 공고 단위로 참여 조건을 다르게 안내할 수 있어야 하므로 meeting_recruit에도 동일한 패턴으로 둔다.
ALTER TABLE meeting_recruit
    ADD COLUMN participation_condition TEXT NULL COMMENT '참여 조건(선택, 예: 성인 및 청소년 단체 신청 가능)' AFTER is_external;
