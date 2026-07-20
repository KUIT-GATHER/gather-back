-- 관심모임 북마크. 기존 posting 전용 `bookmark` 테이블(V8)은 건드리지 않고 대칭 테이블을 신규로 둔다(devplan2 2-1절).
-- V8은 users가 Flyway 미관리 도메인이던 시절이라 FK가 없었지만, V9 이후 users는 Flyway 관리 대상이고
-- post(V12)/social_account(V18)/refresh_token(V9) 모두 users(id) FK를 걸고 있어 그 컨벤션을 따른다.
-- 회원 탈퇴는 users.status를 WITHDRAWN으로 바꾸는 소프트 삭제이고 물리 삭제 로직이 없으므로
-- ON DELETE를 별도로 지정하지 않는다(기본 RESTRICT, 다른 users FK와 동일).
CREATE TABLE meeting_bookmark (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    user_id    BIGINT      NOT NULL,
    meeting_id BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_bookmark_user_meeting UNIQUE (user_id, meeting_id),
    CONSTRAINT fk_meeting_bookmark_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id),
    CONSTRAINT fk_meeting_bookmark_user FOREIGN KEY (user_id) REFERENCES users (id)
);
