package com.gather.gather.domain.notification.controller;

import com.gather.gather.domain.notification.dto.NotificationResponse;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.service.NotificationQueryService;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @Operation(
            summary = "알림 목록 조회",
            description = "로그인한 사용자의 알림을 활동 또는 모임 카테고리별로 조회합니다. " + "삭제된 알림은 제외하고 최신순으로 반환합니다.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @Parameter(description = "알림 카테고리", example = "MEETING") @RequestParam
                    NotificationCategory category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {

        return ApiResponse.success(notificationQueryService.getNotifications(category, pageable));
    }

    @Operation(summary = "알림 읽음 처리", description = "로그인한 사용자의 특정 알림을 읽음 처리합니다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable Long notificationId) {

        return ApiResponse.success(notificationQueryService.markAsRead(notificationId));
    }

    @Operation(summary = "현재 탭 전체 읽음 처리", description = "로그인한 사용자의 활동 또는 모임 카테고리 알림을 모두 읽음 처리합니다.")
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(
            @Parameter(description = "알림 카테고리", example = "MEETING") @RequestParam
                    NotificationCategory category) {

        notificationQueryService.markAllAsRead(category);
        return ApiResponse.success(null);
    }

    @Operation(summary = "알림 삭제", description = "로그인한 사용자의 특정 알림을 삭제합니다.")
    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> deleteNotification(@PathVariable Long notificationId) {

        notificationQueryService.deleteNotification(notificationId);
        return ApiResponse.success(null);
    }
}
