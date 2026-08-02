package com.gather.gather.domain.notification.entity;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.PostNotificationTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_meeting_id")
    private Long targetMeetingId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deduplication_key", length = 120, unique = true)
    private String deduplicationKey;

    private Notification(
            User user,
            NotificationType type,
            String message,
            NotificationTargetType targetType,
            Long targetId,
            Long targetMeetingId) {
        this.user = user;
        this.category = type.getCategory();
        this.type = type;
        this.message = message;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetMeetingId = targetMeetingId;
    }

    public static Notification create(
            User user,
            NotificationType type,
            String message,
            NotificationTargetType targetType,
            Long targetId) {
        return create(user, type, message, targetType, targetId, null);
    }

    public static Notification create(
            User user,
            NotificationType type,
            String message,
            NotificationTargetType targetType,
            Long targetId,
            Long targetMeetingId) {
        validateTarget(targetType, targetId, targetMeetingId);
        return new Notification(user, type, message, targetType, targetId, targetMeetingId);
    }

    public static Notification createPost(
            User user, NotificationType type, String message, PostNotificationTarget target) {
        return new Notification(
                user,
                type,
                message,
                NotificationTargetType.POST,
                target.postId(),
                target.meetingId());
    }

    public static Notification createScheduled(
            User user,
            NotificationType type,
            String message,
            NotificationTargetType targetType,
            Long targetId,
            String deduplicationKey) {

        validateTarget(targetType, targetId, null);

        Notification notification =
                new Notification(user, type, message, targetType, targetId, null);
        notification.deduplicationKey = deduplicationKey;

        return notification;
    }

    private static void validateTarget(
            NotificationTargetType targetType, Long targetId, Long targetMeetingId) {
        if (targetType != NotificationTargetType.MY_PAGE && targetId == null) {
            throw new IllegalArgumentException("이동 대상 ID가 필요한 알림입니다.");
        }
        if (targetType == NotificationTargetType.POST && targetMeetingId == null) {
            throw new IllegalArgumentException("게시글 이동에 모임 ID가 필요합니다.");
        }
        if (targetType != NotificationTargetType.POST && targetMeetingId != null) {
            throw new IllegalArgumentException("게시글 외 이동 대상에는 모임 ID를 지정할 수 없습니다.");
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markAsRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public void delete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }
}
