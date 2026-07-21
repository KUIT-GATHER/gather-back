CREATE TABLE profile_image_upload (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    expected_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6) NULL,
    previous_object_key VARCHAR(255) NULL,
    previous_object_deleted BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    CONSTRAINT uk_profile_image_upload_object_key UNIQUE (object_key),
    CONSTRAINT fk_profile_image_upload_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_profile_image_upload_pending (user_id, status, expires_at),
    INDEX idx_profile_image_upload_cleanup (status, previous_object_deleted, applied_at)
);
