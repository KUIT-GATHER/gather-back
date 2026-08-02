package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.PostNotificationTarget;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingPostNotificationService {

    private final MeetingMemberRepository meetingMemberRepository;
    private final NotificationWriter notificationWriter;

    /** 모임 게시글 등록 알림은 현재 기획상 별도 opt-out 설정 없이 승인된 활성 모임원에게 항상 발송한다. */
    @Transactional(readOnly = true)
    public void createNotifications(
            Long meetingId, Long postId, Long authorId, NotificationType type, String message) {
        List<Long> recipientUserIds =
                meetingMemberRepository
                        .findAllByMeetingIdAndStatusFetchUser(
                                meetingId, MeetingMemberStatus.APPROVED)
                        .stream()
                        .map(MeetingMember::getUser)
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                        .map(user -> user.getId())
                        .filter(userId -> !userId.equals(authorId))
                        .distinct()
                        .toList();

        if (recipientUserIds.isEmpty()) {
            log.debug(
                    "모임 게시글 알림 수신자 없음. meetingId={}, postId={}, authorId={}",
                    meetingId,
                    postId,
                    authorId);
            return;
        }

        PostNotificationTarget target = new PostNotificationTarget(postId, meetingId);
        int successCount = 0;
        for (Long recipientUserId : recipientUserIds) {
            try {
                notificationWriter.createPost(recipientUserId, type, message, target);
                successCount++;
            } catch (RuntimeException exception) {
                log.warn(
                        "모임 게시글 알림 개별 생성 실패. recipientUserId={}, meetingId={}, postId={}",
                        recipientUserId,
                        meetingId,
                        postId,
                        exception);
            }
        }

        log.info(
                "모임 게시글 알림 생성 완료. meetingId={}, postId={}, requestedCount={}, successCount={}, failureCount={}",
                meetingId,
                postId,
                recipientUserIds.size(),
                successCount,
                recipientUserIds.size() - successCount);
    }
}
