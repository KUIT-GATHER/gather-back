CREATE TABLE password_reset_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_user
        UNIQUE (user_id),
    CONSTRAINT uk_password_reset_token_token_hash
        UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT chk_password_reset_token_expiry
        CHECK (expires_at > created_at),
    INDEX idx_password_reset_token_expires_at
        (expires_at)
);
