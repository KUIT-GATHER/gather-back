CREATE TABLE post (
                      id               BIGINT       NOT NULL AUTO_INCREMENT,
                      meeting_id       BIGINT       NOT NULL,
                      user_id          BIGINT       NOT NULL,
                      title            VARCHAR(255) NOT NULL,
                      content          TEXT         NOT NULL,
                      type             VARCHAR(30)  NOT NULL COMMENT 'NOTICE / REVIEW / RECRUIT / FREE',
                      recruit_capacity INT          NULL COMMENT 'RECRUIT 유형일 때만 사용',
                      like_count       INT          NOT NULL DEFAULT 0,
                      comment_count    INT          NOT NULL DEFAULT 0,
                      created_at       DATETIME(6)  NOT NULL,
                      updated_at       DATETIME(6)  NULL,
                      deleted_at       DATETIME(6)  NULL,
                      PRIMARY KEY (id),
                      CONSTRAINT fk_post_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id),
                      CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_post_meeting_type_created ON post (meeting_id, type, created_at);