ALTER TABLE volunteer_posting
    ADD COLUMN source VARCHAR(20) NULL COMMENT 'PostingSource enum (API_1365/VMS_CRAWL)';

UPDATE volunteer_posting SET source = 'API_1365' WHERE source IS NULL;

ALTER TABLE volunteer_posting
    MODIFY COLUMN source VARCHAR(20) NOT NULL;
