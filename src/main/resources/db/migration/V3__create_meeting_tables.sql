CREATE TABLE meeting (
                         id BIGINT NOT NULL AUTO_INCREMENT,
                         name VARCHAR(255) NOT NULL,
                         description VARCHAR(255) NULL,
                         max_member INT NOT NULL,
                         current_member_count INT NOT NULL,
                         deadline DATETIME NOT NULL,
                         memo VARCHAR(255) NULL,
                         category_id BIGINT NOT NULL,
                         region_id BIGINT NOT NULL,
                         host_id BIGINT NOT NULL,
                         volunteer_posting_id BIGINT NULL,
                         participation_condition VARCHAR(255) NULL,
                         status VARCHAR(30) NOT NULL,
                         activity_start_at DATETIME NOT NULL,
                         activity_end_at DATETIME NOT NULL,
                         created_at DATETIME NOT NULL,
                         updated_at DATETIME NULL,
                         deleted_at DATETIME NULL,
                         PRIMARY KEY (id),
                         CONSTRAINT fk_meeting_category FOREIGN KEY (category_id) REFERENCES categories (id),
                         CONSTRAINT fk_meeting_region FOREIGN KEY (region_id) REFERENCES region (id)
);

CREATE TABLE meeting_member (
                                id BIGINT NOT NULL AUTO_INCREMENT,
                                user_id BIGINT NOT NULL,
                                meeting_id BIGINT NOT NULL,
                                role VARCHAR(30) NOT NULL,
                                status VARCHAR(30) NOT NULL,
                                joined_at DATETIME NOT NULL,
                                created_at DATETIME NOT NULL,
                                updated_at DATETIME NULL,
                                PRIMARY KEY (id),
                                CONSTRAINT fk_meeting_member_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id),
                                CONSTRAINT uk_meeting_member_user_meeting UNIQUE (user_id, meeting_id)
);