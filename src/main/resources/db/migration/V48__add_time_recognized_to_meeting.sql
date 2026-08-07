-- 모임 정보 수정 정책: 공고 기반 모임에 봉사시간 인정 여부를 추가한다.
-- MeetingHomeResponse의 임시 필드 timeVerified(항상 false)를 대체한다.
ALTER TABLE meeting
    ADD COLUMN time_recognized BIT(1) NOT NULL DEFAULT 0 COMMENT '봉사시간 인정 여부(공고 기반 모임 전용, 자유 모임은 항상 false)';
