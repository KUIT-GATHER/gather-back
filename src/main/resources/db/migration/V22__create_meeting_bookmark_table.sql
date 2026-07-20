-- 관심모임 북마크. 기존 posting 전용 `bookmark` 테이블(V8)은 건드리지 않고 대칭 테이블을 신규로 둔다(devplan2 2-1절).
CREATE TABLE meeting_bookmark (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    user_id    BIGINT      NOT NULL,
    meeting_id BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_bookmark_user_meeting UNIQUE (user_id, meeting_id),
    CONSTRAINT fk_meeting_bookmark_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id)
);
