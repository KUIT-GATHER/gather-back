CREATE TABLE phone_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    verification_id VARCHAR(36) NOT NULL,
    phone_number VARCHAR(11) NOT NULL,
    verification_code VARCHAR(32) NOT NULL,
    verified BIT(1) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    consumed_at DATETIME(6) NULL,
    last_confirm_attempt_at DATETIME(6) NULL,
    confirm_attempt_count INT NOT NULL DEFAULT 0,
    last_qr_requested_at DATETIME(6) NULL,
    qr_request_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_phone_verification_verification_id UNIQUE (verification_id),
    INDEX idx_phone_verification_phone_created (phone_number, created_at),
    INDEX idx_phone_verification_expires_at (expires_at)
);
