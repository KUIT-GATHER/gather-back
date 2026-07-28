package com.gather.gather.domain.notification.controller;

import com.gather.gather.domain.notification.dto.NotificationSettingResponse;
import com.gather.gather.domain.notification.dto.NotificationSettingUpdateRequest;
import com.gather.gather.domain.notification.service.NotificationSettingService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification Setting", description = "알림 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/settings")
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @Operation(
            summary = "알림 설정 조회",
            description = "로그인한 사용자의 활동 및 모임 알림 설정을 조회합니다. " + "저장된 설정이 없으면 기본 설정을 생성해 반환합니다.")
    @GetMapping
    public ApiResponse<NotificationSettingResponse> getSettings() {

        return ApiResponse.success(notificationSettingService.getSettings());
    }

    @Operation(summary = "알림 설정 변경", description = "로그인한 사용자의 활동 및 모임 알림 설정 전체를 변경합니다.")
    @PutMapping
    public ApiResponse<NotificationSettingResponse> updateSettings(
            @Valid @RequestBody NotificationSettingUpdateRequest request) {

        return ApiResponse.success(notificationSettingService.updateSettings(request));
    }
}
