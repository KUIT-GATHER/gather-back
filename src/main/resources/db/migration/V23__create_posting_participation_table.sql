-- 활동 상세보기의 4단계 진행상태(APPLIED/CONFIRMED/COMPLETED/REVIEWED) 추적용 신규 엔티티(devplan2 2-2절).
-- status에는 DB 레벨 CHECK 제약을 걸지 않는다(다른 status 컬럼과 동일 컨벤션, 값 검증은 Java enum이 담당).
-- 취소는 물리 삭제로 처리한다(Day6 결정). 재신청 시 새 행을 INSERT하므로 (user_id, posting_id) UNIQUE로
-- 동시 중복 신청과 활성 참여 중복을 DB 레벨에서 막는다. COMPLETED/REVIEWED 상태의 행은 이력 보존을 위해
-- 애플리케이션에서 삭제(취소)를 금지해야 한다 — 삭제(취소)는 APPLIED/CONFIRMED 상태에서만 허용한다.
-- UNIQUE 인덱스가 (user_id, posting_id) 순서라 user_id 단독 조회도 이 인덱스로 커버되므로
-- 기존 idx_posting_participation_user 단일 컬럼 인덱스는 중복이라 제거한다.
-- V9 이후 users는 Flyway 관리 대상이고 post(V12)/social_account(V18)/refresh_token(V9) 모두
-- users(id) FK를 걸므로 그 컨벤션을 따른다. 회원 탈퇴는 users.status를 WITHDRAWN으로 바꾸는
-- 소프트 삭제이고 물리 삭제 로직이 없으므로 ON DELETE는 기본 RESTRICT를 그대로 둔다.
CREATE TABLE posting_participation (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT 'users 테이블 참조',
    posting_id BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_posting_participation_posting FOREIGN KEY (posting_id) REFERENCES volunteer_posting (id),
    CONSTRAINT fk_posting_participation_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_posting_participation_user_posting UNIQUE (user_id, posting_id)
);
