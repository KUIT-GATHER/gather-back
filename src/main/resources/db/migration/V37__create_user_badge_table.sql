CREATE TABLE user_badge
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    badge_type VARCHAR(30) NOT NULL,
    earned_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_badge_user_type UNIQUE (user_id, badge_type),
    CONSTRAINT fk_user_badge_user FOREIGN KEY (user_id) REFERENCES users (id)
);
