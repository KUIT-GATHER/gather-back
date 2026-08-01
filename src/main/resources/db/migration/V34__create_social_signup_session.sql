CREATE TABLE social_signup_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_key VARCHAR(64) NOT NULL,
    provider_user_key_version INT NOT NULL,
    provider_user_id_ciphertext VARCHAR(512) NOT NULL,
    encryption_key_version INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_signup_session_token_hash
        UNIQUE (token_hash),
    INDEX idx_social_signup_session_provider_key_status
        (provider, provider_user_key, status),
    INDEX idx_social_signup_session_expires_at
        (expires_at)
);
