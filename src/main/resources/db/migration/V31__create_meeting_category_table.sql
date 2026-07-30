CREATE TABLE meeting_category (
    meeting_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    PRIMARY KEY (meeting_id, category),
    INDEX idx_meeting_category_category_meeting (category, meeting_id),
    CONSTRAINT fk_meeting_category_meeting
        FOREIGN KEY (meeting_id)
        REFERENCES meeting (id)
        ON DELETE CASCADE
);

INSERT INTO meeting_category (meeting_id, category)
SELECT id, category
FROM meeting;

ALTER TABLE meeting
    DROP COLUMN category;
