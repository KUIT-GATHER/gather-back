ALTER TABLE social_account
    MODIFY COLUMN provider_user_id VARCHAR(100) NULL;

ALTER TABLE kakao_unlink_task
    ADD COLUMN retry_cycle INT NOT NULL DEFAULT 0 AFTER status,
    ADD CONSTRAINT chk_kakao_unlink_task_retry_cycle
        CHECK (retry_cycle >= 0),
    ADD CONSTRAINT chk_kakao_unlink_task_attempt_count
        CHECK (attempt_count BETWEEN 0 AND 12);

CREATE TABLE kakao_unlink_worker_control (
    id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    blocked_at DATETIME(6) NULL,
    blocked_reason VARCHAR(40) NULL,
    last_http_status INT NULL,
    last_kakao_code INT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT chk_kakao_unlink_worker_control_status
        CHECK (status IN ('ACTIVE', 'CONFIGURATION_BLOCKED'))
);

INSERT INTO kakao_unlink_worker_control (
    id,
    status,
    blocked_at,
    blocked_reason,
    last_http_status,
    last_kakao_code,
    updated_at,
    version
) VALUES (
    1,
    'ACTIVE',
    NULL,
    NULL,
    NULL,
    NULL,
    UTC_TIMESTAMP(6),
    0
);
