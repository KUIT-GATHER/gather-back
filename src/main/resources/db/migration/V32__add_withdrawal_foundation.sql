ALTER TABLE users
    MODIFY COLUMN name VARCHAR(20) NULL,
    MODIFY COLUMN birth_date DATE NULL,
    MODIFY COLUMN gender ENUM ('FEMALE', 'MALE') NULL,
    MODIFY COLUMN phone_number VARCHAR(24) NOT NULL,
    MODIFY COLUMN nickname VARCHAR(24) NOT NULL,
    MODIFY COLUMN activity_region_id BIGINT NULL,
    ADD COLUMN withdrawn_at DATETIME(6) NULL,
    ADD COLUMN withdrawal_reason VARCHAR(20) NULL,
    ADD COLUMN anonymized_at DATETIME(6) NULL;

CREATE TABLE account_rejoin_block (
    id BIGINT NOT NULL AUTO_INCREMENT,
    identifier_type VARCHAR(10) NOT NULL,
    identifier_hash VARCHAR(64) NOT NULL,
    key_version INT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    source_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_rejoin_block_identifier
        UNIQUE (identifier_type, identifier_hash),
    CONSTRAINT fk_account_rejoin_block_source_user
        FOREIGN KEY (source_user_id) REFERENCES users (id),
    INDEX idx_account_rejoin_block_expires_at (expires_at)
);
