ALTER TABLE phone_verification
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'SIGNUP' AFTER phone_number,
    ADD INDEX idx_phone_verification_created_at (created_at);
