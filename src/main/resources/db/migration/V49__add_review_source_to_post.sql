ALTER TABLE post
    ADD COLUMN review_source_type VARCHAR(20) NULL AFTER comment_count,
    ADD COLUMN review_source_id BIGINT NULL AFTER review_source_type;

CREATE INDEX idx_post_review_source ON post (review_source_type, review_source_id);
