package com.gather.gather.domain.notification.model;

public record PostNotificationTarget(Long postId, Long meetingId) {

    public PostNotificationTarget {
        if (postId == null || meetingId == null) {
            throw new IllegalArgumentException("게시글 알림 대상 ID는 null일 수 없습니다.");
        }
    }
}
