CREATE TABLE notification_setting (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      user_id BIGINT NOT NULL,

                                      volunteer_schedule_enabled BIT(1) NOT NULL DEFAULT 1,
                                      bookmarked_posting_deadline_enabled BIT(1) NOT NULL DEFAULT 0,
                                      badge_enabled BIT(1) NOT NULL DEFAULT 0,
                                      activity_post_comment_enabled BIT(1) NOT NULL DEFAULT 0,

                                      meeting_join_result_enabled BIT(1) NOT NULL DEFAULT 1,
                                      bookmarked_meeting_deadline_enabled BIT(1) NOT NULL DEFAULT 0,
                                      meeting_post_comment_enabled BIT(1) NOT NULL DEFAULT 0,

                                      created_at DATETIME(6) NOT NULL,
                                      updated_at DATETIME(6) NULL,

                                      PRIMARY KEY (id),
                                      CONSTRAINT uk_notification_setting_user UNIQUE (user_id),
                                      CONSTRAINT fk_notification_setting_user
                                          FOREIGN KEY (user_id) REFERENCES users (id)
);