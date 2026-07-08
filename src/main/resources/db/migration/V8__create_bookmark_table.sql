CREATE TABLE bookmark (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    user_id    BIGINT      NOT NULL COMMENT 'users 테이블 참조. 아직 Flyway 미관리 도메인이라 FK 없이 ID만 보관',
    posting_id BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bookmark_user_posting UNIQUE (user_id, posting_id),
    CONSTRAINT fk_bookmark_posting FOREIGN KEY (posting_id) REFERENCES volunteer_posting (id)
);
