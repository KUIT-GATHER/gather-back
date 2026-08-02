ALTER TABLE notification
    ADD COLUMN deduplication_key VARCHAR(120) NULL;

CREATE UNIQUE INDEX uq_notification_deduplication_key
    ON notification (deduplication_key);