package com.gather.gather.domain.meeting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.service.MeetingKeywordRecommendationService;
import com.gather.gather.domain.meeting.service.MeetingRecommendationService;
import com.gather.gather.domain.meeting.service.MeetingService;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeetingController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeetingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MeetingService meetingService;

    @MockitoBean private MeetingKeywordRecommendationService meetingKeywordRecommendationService;

    @MockitoBean private MeetingRecommendationService meetingRecommendationService;

    @Test
    @DisplayName("POST /api/v1/meetings returns 400 when name exceeds 100 characters")
    void createMeeting_returns400_whenNameExceeds100Characters() throws Exception {
        String requestBody =
                """
                {
                  "name": "%s",
                  "maxMember": 2,
                  "deadline": "2026-07-31T23:59:59",
                  "categories": ["WELFARE"],
                  "regionId": 1,
                  "activityStartAt": "2026-08-01T09:00:00",
                  "activityEndAt": "2026-08-01T18:00:00"
                }
                """
                        .formatted("a".repeat(101));

        mockMvc.perform(
                        post("/api/v1/meetings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(meetingService);
    }

    @Test
    @DisplayName("DELETE /api/v1/meetings/{meetingId}/join cancels the caller's pending join request")
    void cancelMyJoinRequest_returns200_andCallsService() throws Exception {
        mockMvc.perform(delete("/api/v1/meetings/1/join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(meetingService).cancelMyJoinRequest(1L);
    }

    @Test
    @DisplayName("GET /api/v1/meetings/recommended returns 200 with recommended meetings")
    void getRecommendedMeetings_returns200WithRecommendations() throws Exception {
        MeetingResponse response =
                new MeetingResponse(
                        1L,
                        "동구 환경정화 모임",
                        "설명",
                        3,
                        10,
                        2L,
                        "동구",
                        Set.of(PostingCategory.ENVIRONMENT),
                        MeetingStatus.RECRUITING,
                        LocalDateTime.of(2026, 7, 31, 23, 59, 59),
                        LocalDateTime.of(2026, 8, 1, 9, 0, 0),
                        null,
                        null);
        when(meetingRecommendationService.getRecommendedMeetings()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/meetings/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].meetingId").value(1))
                .andExpect(jsonPath("$.data[0].categories[0]").value("ENVIRONMENT"));

        verify(meetingRecommendationService).getRecommendedMeetings();
    }

    @Test
    @DisplayName("GET /api/v1/meetings/recommended returns 200 with empty list when no data")
    void getRecommendedMeetings_returns200WithEmptyList_whenNoData() throws Exception {
        when(meetingRecommendationService.getRecommendedMeetings()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/meetings/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/meetings creates a free meeting without an activity period")
    void createMeeting_returns201_whenFreeMeetingHasNoActivityPeriod() throws Exception {
        String requestBody =
                """
                {
                  "name": "자유 모임",
                  "description": "활동 일정은 추후 등록합니다.",
                  "maxMember": 10,
                  "deadline": "2026-08-01T23:59:59",
                  "categories": ["ENVIRONMENT", "EDUCATION"],
                  "regionId": 1,
                  "participationCondition": "누구나 참여할 수 있습니다."
                }
                """;

        mockMvc.perform(
                        post("/api/v1/meetings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<MeetingCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(MeetingCreateRequest.class);
        verify(meetingService).createMeeting(requestCaptor.capture());

        MeetingCreateRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.volunteerPostingId()).isNull();
        assertThat(capturedRequest.categories())
                .containsExactlyInAnyOrder(PostingCategory.ENVIRONMENT, PostingCategory.EDUCATION);
        assertThat(capturedRequest.activityStartAt()).isNull();
        assertThat(capturedRequest.activityEndAt()).isNull();
    }

    @Test
    @DisplayName("POST /api/v1/meetings returns 400 when more than 3 categories are requested")
    void createMeeting_returns400_whenMoreThanThreeCategoriesAreRequested() throws Exception {
        String requestBody =
                """
                {
                  "name": "자유 모임",
                  "description": "카테고리 개수 검증",
                  "maxMember": 10,
                  "deadline": "2026-08-01T23:59:59",
                  "categories": ["ENVIRONMENT", "EDUCATION", "CULTURE", "WELFARE"],
                  "regionId": 1
                }
                """;

        mockMvc.perform(
                        post("/api/v1/meetings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(meetingService);
    }

    @Test
    @DisplayName("PATCH /api/v1/meetings/{id}/complete returns 200 on completion")
    void completeMeeting_returns200() throws Exception {
        mockMvc.perform(patch("/api/v1/meetings/12/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(meetingService).completeMeeting(12L);
    }

    @Test
    @DisplayName("PATCH /api/v1/meetings/{id}/complete returns 403 when called by a non-host")
    void completeMeeting_returns403_whenNotHost() throws Exception {
        org.mockito.Mockito.doThrow(
                        new com.gather.gather.global.exception.BusinessException(
                                com.gather.gather.global.exception.ErrorCode.MEETING_HOST_ONLY))
                .when(meetingService)
                .completeMeeting(12L);

        mockMvc.perform(patch("/api/v1/meetings/12/complete"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEETING_HOST_ONLY"));
    }

    @Test
    @DisplayName("PATCH /api/v1/meetings/{id}/members/me/hours returns 200 on submission")
    void submitMemberHours_returns200() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/meetings/12/members/me/hours")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recognizedMinutes\": 210}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(meetingService).submitMemberHours(12L, 210);
    }
}
