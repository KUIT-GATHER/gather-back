ALTER TABLE users
    MODIFY COLUMN status ENUM ('ACTIVE', 'SUSPENDED', 'WITHDRAWAL_PENDING', 'WITHDRAWN') NOT NULL;

CREATE TABLE kakao_unlink_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    social_account_id BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    last_attempt_at DATETIME(6) NULL,
    claim_token VARCHAR(64) NULL,
    claimed_by VARCHAR(128) NULL,
    claimed_at DATETIME(6) NULL,
    lease_expires_at DATETIME(6) NULL,
    last_http_status INT NULL,
    last_kakao_code INT NULL,
    last_error_type VARCHAR(40) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_kakao_unlink_task_social_account_generation
        UNIQUE (social_account_id, generation),
    CONSTRAINT fk_kakao_unlink_task_social_account
        FOREIGN KEY (social_account_id) REFERENCES social_account (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    INDEX idx_kakao_unlink_task_due
        (status, next_attempt_at, id),
    INDEX idx_kakao_unlink_task_lease_recovery
        (status, lease_expires_at, id)
);
