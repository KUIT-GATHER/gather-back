package com.gather.gather.domain.notification.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.notification.dto.NotificationResponse;
import com.gather.gather.domain.notification.dto.NotificationUnreadCountResponse;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.service.NotificationQueryService;
import com.gather.gather.global.common.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationQueryService notificationQueryService;

    @Test
    @DisplayName("카테고리별 알림 목록을 조회한다")
    void getNotifications_returnsNotifications() throws Exception {
        NotificationResponse notification =
                new NotificationResponse(
                        1L,
                        NotificationCategory.MEETING,
                        NotificationType.MEETING_JOIN_APPROVED,
                        "[모임명] 가입이 승인되었어요.",
                        NotificationTargetType.MEETING,
                        10L,
                        null,
                        "https://example.com/meeting-thumbnail.jpg",
                        false,
                        LocalDateTime.of(2026, 7, 27, 12, 0));

        when(notificationQueryService.getNotifications(
                        any(NotificationCategory.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(notification), 1, 1, 0, 20));

        mockMvc.perform(get("/api/v1/notifications").param("category", "MEETING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].category").value("MEETING"))
                .andExpect(jsonPath("$.data.content[0].targetMeetingId").value(nullValue()))
                .andExpect(
                        jsonPath("$.data.content[0].thumbnailUrl")
                                .value("https://example.com/meeting-thumbnail.jpg"))
                .andExpect(jsonPath("$.data.content[0].read").value(false));
    }

    @Test
    @DisplayName("게시글 알림 응답에는 딥링크에 필요한 모임 ID가 포함된다")
    void getNotificationsReturnsTargetMeetingIdForPostNotification() throws Exception {
        NotificationResponse notification =
                new NotificationResponse(
                        2L,
                        NotificationCategory.MEETING,
                        NotificationType.MEETING_POST_CREATED,
                        "[모임명]에 작성자님이 새 게시글을 등록했어요.",
                        NotificationTargetType.POST,
                        10L,
                        3L,
                        "https://example.com/meeting-thumbnail.jpg",
                        false,
                        LocalDateTime.of(2026, 7, 27, 12, 0));

        when(notificationQueryService.getNotifications(
                        any(NotificationCategory.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(notification), 1, 1, 0, 20));

        mockMvc.perform(get("/api/v1/notifications").param("category", "MEETING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].targetType").value("POST"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(10))
                .andExpect(jsonPath("$.data.content[0].targetMeetingId").value(3))
                .andExpect(
                        jsonPath("$.data.content[0].thumbnailUrl")
                                .value("https://example.com/meeting-thumbnail.jpg"));
    }

    @Test
    @DisplayName("알림을 읽음 처리한다")
    void markAsRead_returnsReadNotification() throws Exception {
        NotificationResponse notification =
                new NotificationResponse(
                        1L,
                        NotificationCategory.MEETING,
                        NotificationType.MEETING_JOIN_APPROVED,
                        "[모임명] 가입이 승인되었어요.",
                        NotificationTargetType.MEETING,
                        10L,
                        null,
                        "https://example.com/meeting-thumbnail.jpg",
                        true,
                        LocalDateTime.of(2026, 7, 27, 12, 0));

        when(notificationQueryService.markAsRead(1L)).thenReturn(notification);

        mockMvc.perform(patch("/api/v1/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(
                        jsonPath("$.data.thumbnailUrl")
                                .value("https://example.com/meeting-thumbnail.jpg"));
    }

    @Test
    @DisplayName("대표 이미지가 없는 알림은 thumbnailUrl을 null로 반환한다")
    void getNotificationsReturnsNullThumbnailWhenImageDoesNotExist() throws Exception {
        NotificationResponse notification =
                new NotificationResponse(
                        3L,
                        NotificationCategory.ACTIVITY,
                        NotificationType.BADGE_EARNED,
                        "새로운 배지를 획득했어요.",
                        NotificationTargetType.MY_PAGE,
                        null,
                        null,
                        null,
                        false,
                        LocalDateTime.of(2026, 7, 27, 12, 0));

        when(notificationQueryService.getNotifications(
                        any(NotificationCategory.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(notification), 1, 1, 0, 20));

        mockMvc.perform(get("/api/v1/notifications").param("category", "ACTIVITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("BADGE_EARNED"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value(nullValue()));
    }

    @Test
    @DisplayName("현재 카테고리의 알림을 전체 읽음 처리한다")
    void markAllAsRead_returnsSuccess() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all").param("category", "ACTIVITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationQueryService).markAllAsRead(NotificationCategory.ACTIVITY);
    }

    @Test
    @DisplayName("알림을 삭제한다")
    void deleteNotification_returnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationQueryService).deleteNotification(1L);
    }

    @Test
    @DisplayName("미읽음 알림 개수를 조회한다")
    void getUnreadCount() throws Exception {
        // given
        NotificationUnreadCountResponse response = NotificationUnreadCountResponse.of(2L, 3L);

        when(notificationQueryService.getUnreadCount()).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activity").value(2))
                .andExpect(jsonPath("$.data.meeting").value(3))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(notificationQueryService).getUnreadCount();
    }
}
