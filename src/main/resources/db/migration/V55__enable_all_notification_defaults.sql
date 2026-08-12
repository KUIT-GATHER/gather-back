ALTER TABLE notification_setting
    ALTER COLUMN volunteer_schedule_enabled SET DEFAULT 1,
    ALTER COLUMN bookmarked_posting_deadline_enabled SET DEFAULT 1,
    ALTER COLUMN badge_enabled SET DEFAULT 1,
    ALTER COLUMN activity_post_comment_enabled SET DEFAULT 1,
    ALTER COLUMN meeting_join_result_enabled SET DEFAULT 1,
    ALTER COLUMN bookmarked_meeting_deadline_enabled SET DEFAULT 1,
    ALTER COLUMN meeting_post_comment_enabled SET DEFAULT 1;
