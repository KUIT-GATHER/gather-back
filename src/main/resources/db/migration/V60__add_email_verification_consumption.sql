ALTER TABLE email_verification
    ADD COLUMN verification_id VARCHAR(36) NULL AFTER email,
    ADD COLUMN consumed_at DATETIME(6) NULL AFTER verified_at;

UPDATE email_verification
SET verification_id = UUID()
WHERE verification_id IS NULL;

ALTER TABLE email_verification
    MODIFY COLUMN verification_id VARCHAR(36) NOT NULL,
    ADD CONSTRAINT uk_email_verification_verification_id UNIQUE (verification_id);
