CREATE TABLE volunteer_posting_location (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    posting_id    BIGINT       NOT NULL COMMENT 'volunteer_posting.id 참조',
    location_seq  INT          NOT NULL COMMENT '2 또는 3 (1번째 장소는 volunteer_posting 자신의 컬럼)',
    address       VARCHAR(255) NOT NULL,
    latitude      DECIMAL(10, 7) NULL,
    longitude     DECIMAL(11, 7) NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_posting_location_posting_id_seq UNIQUE (posting_id, location_seq),
    CONSTRAINT fk_volunteer_posting_location_posting FOREIGN KEY (posting_id) REFERENCES volunteer_posting (id)
);
