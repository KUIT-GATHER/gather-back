CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    type VARCHAR(40) NOT NULL,
    message VARCHAR(255) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_notification_user_category_created (
        user_id,
        category,
        deleted_at,
        created_at
    )
);
