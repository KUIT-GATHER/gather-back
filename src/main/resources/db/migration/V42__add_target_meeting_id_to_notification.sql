ALTER TABLE notification
    ADD COLUMN target_meeting_id BIGINT NULL AFTER target_id;
