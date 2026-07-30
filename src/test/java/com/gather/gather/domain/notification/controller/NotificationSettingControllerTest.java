package com.gather.gather.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.notification.dto.NotificationSettingResponse;
import com.gather.gather.domain.notification.dto.NotificationSettingUpdateRequest;
import com.gather.gather.domain.notification.service.NotificationSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationSettingController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationSettingControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("알림 설정을 조회한다")
    void getSettings_returnsSettings() throws Exception {
        when(notificationSettingService.getSettings()).thenReturn(defaultResponse());

        mockMvc.perform(get("/api/v1/notifications/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data" + ".volunteerScheduleEnabled").value(true))
                .andExpect(jsonPath("$.data" + ".meetingJoinResultEnabled").value(true));
    }

    @Test
    @DisplayName("알림 설정 전체를 변경한다")
    void updateSettings_returnsUpdatedSettings() throws Exception {
        NotificationSettingUpdateRequest request =
                new NotificationSettingUpdateRequest(false, true, true, true, false, true, true);

        NotificationSettingResponse response =
                new NotificationSettingResponse(false, true, true, true, false, true, true);

        when(notificationSettingService.updateSettings(any(NotificationSettingUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/notifications/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data" + ".volunteerScheduleEnabled").value(false))
                .andExpect(jsonPath("$.data" + ".bookmarkedPostingDeadlineEnabled").value(true));
    }

    @Test
    @DisplayName("필수 알림 설정이 누락되면 400을 반환한다")
    void updateSettings_returnsBadRequestWhenFieldMissing() throws Exception {

        String request =
                """
                {
                  "bookmarkedPostingDeadlineEnabled": false,
                  "badgeEnabled": false,
                  "activityPostCommentEnabled": false,
                  "meetingJoinResultEnabled": true,
                  "bookmarkedMeetingDeadlineEnabled": false,
                  "meetingPostCommentEnabled": false
                }
                """;

        mockMvc.perform(
                        put("/api/v1/notifications/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private NotificationSettingResponse defaultResponse() {
        return new NotificationSettingResponse(true, false, false, false, true, false, false);
    }
}
