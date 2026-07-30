package com.gather.gather.domain.notification.entity;

import com.gather.gather.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Table(name = "notification_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    private static final boolean DEFAULT_VOLUNTEER_SCHEDULE = true;
    private static final boolean DEFAULT_BOOKMARKED_POSTING_DEADLINE = false;
    private static final boolean DEFAULT_BADGE = false;
    private static final boolean DEFAULT_ACTIVITY_POST_COMMENT = false;

    private static final boolean DEFAULT_MEETING_JOIN_RESULT = true;
    private static final boolean DEFAULT_BOOKMARKED_MEETING_DEADLINE = false;
    private static final boolean DEFAULT_MEETING_POST_COMMENT = false;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "volunteer_schedule_enabled", nullable = false)
    private boolean volunteerScheduleEnabled;

    @Column(name = "bookmarked_posting_deadline_enabled", nullable = false)
    private boolean bookmarkedPostingDeadlineEnabled;

    @Column(name = "badge_enabled", nullable = false)
    private boolean badgeEnabled;

    @Column(name = "activity_post_comment_enabled", nullable = false)
    private boolean activityPostCommentEnabled;

    @Column(name = "meeting_join_result_enabled", nullable = false)
    private boolean meetingJoinResultEnabled;

    @Column(name = "bookmarked_meeting_deadline_enabled", nullable = false)
    private boolean bookmarkedMeetingDeadlineEnabled;

    @Column(name = "meeting_post_comment_enabled", nullable = false)
    private boolean meetingPostCommentEnabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private NotificationSetting(User user) {
        this.user = user;

        this.volunteerScheduleEnabled = DEFAULT_VOLUNTEER_SCHEDULE;
        this.bookmarkedPostingDeadlineEnabled = DEFAULT_BOOKMARKED_POSTING_DEADLINE;
        this.badgeEnabled = DEFAULT_BADGE;
        this.activityPostCommentEnabled = DEFAULT_ACTIVITY_POST_COMMENT;

        this.meetingJoinResultEnabled = DEFAULT_MEETING_JOIN_RESULT;
        this.bookmarkedMeetingDeadlineEnabled = DEFAULT_BOOKMARKED_MEETING_DEADLINE;
        this.meetingPostCommentEnabled = DEFAULT_MEETING_POST_COMMENT;
    }

    public static NotificationSetting createDefault(User user) {
        return new NotificationSetting(user);
    }

    public void update(
            boolean volunteerScheduleEnabled,
            boolean bookmarkedPostingDeadlineEnabled,
            boolean badgeEnabled,
            boolean activityPostCommentEnabled,
            boolean meetingJoinResultEnabled,
            boolean bookmarkedMeetingDeadlineEnabled,
            boolean meetingPostCommentEnabled) {

        this.volunteerScheduleEnabled = volunteerScheduleEnabled;
        this.bookmarkedPostingDeadlineEnabled = bookmarkedPostingDeadlineEnabled;
        this.badgeEnabled = badgeEnabled;
        this.activityPostCommentEnabled = activityPostCommentEnabled;

        this.meetingJoinResultEnabled = meetingJoinResultEnabled;
        this.bookmarkedMeetingDeadlineEnabled = bookmarkedMeetingDeadlineEnabled;
        this.meetingPostCommentEnabled = meetingPostCommentEnabled;
    }
}
