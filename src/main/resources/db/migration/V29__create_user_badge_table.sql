-- 사용자별 뱃지 획득 이력. unique(user_id, badge_id)로 중복 획득을 DB 레벨에서도 막는다(devplan2 8-3절).
CREATE TABLE user_badge (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    user_id      BIGINT   NOT NULL COMMENT 'users 테이블 참조',
    badge_id     BIGINT   NOT NULL,
    achieved_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_badge_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_badge_badge FOREIGN KEY (badge_id) REFERENCES badge (id),
    CONSTRAINT uq_user_badge_user_badge UNIQUE (user_id, badge_id)
);
