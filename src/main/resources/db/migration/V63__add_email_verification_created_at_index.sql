ALTER TABLE email_verification
    ADD INDEX idx_email_verification_created_at (created_at);
