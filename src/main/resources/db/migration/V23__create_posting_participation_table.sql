-- 활동 상세보기의 4단계 진행상태(APPLIED/CONFIRMED/COMPLETED/REVIEWED) 추적용 신규 엔티티(devplan2 2-2절).
-- status에는 DB 레벨 CHECK 제약을 걸지 않는다(다른 status 컬럼과 동일 컨벤션, 값 검증은 Java enum이 담당).
-- (user_id, posting_id) UNIQUE를 걸지 않는다 — 취소 후 재신청을 허용해야 하는데, 취소를 물리 삭제로 할지
-- CANCELLED 상태로 남길지가 아직 미정(Day6 결정 사항)이라 스키마에서 미리 막지 않는다.
CREATE TABLE posting_participation (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT 'users 테이블 참조. bookmark(V8)와 동일 컨벤션으로 FK 없이 ID만 보관',
    posting_id BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_posting_participation_posting FOREIGN KEY (posting_id) REFERENCES volunteer_posting (id),
    INDEX idx_posting_participation_user (user_id)
);
