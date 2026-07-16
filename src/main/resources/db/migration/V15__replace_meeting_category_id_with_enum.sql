ALTER TABLE meeting
    ADD COLUMN category VARCHAR(20) NULL;

UPDATE meeting
SET category = 'COMMUNITY'
WHERE category IS NULL;

ALTER TABLE meeting
    MODIFY category VARCHAR(20) NOT NULL;

ALTER TABLE meeting
DROP FOREIGN KEY fk_meeting_category;

ALTER TABLE meeting
DROP COLUMN category_id;