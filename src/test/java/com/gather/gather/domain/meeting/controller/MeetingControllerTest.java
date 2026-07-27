package com.gather.gather.domain.meeting.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.meeting.service.MeetingKeywordRecommendationService;
import com.gather.gather.domain.meeting.service.MeetingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("POST /api/v1/meetings returns 400 when name exceeds 100 characters")
    void createMeeting_returns400_whenNameExceeds100Characters() throws Exception {
        String requestBody =
                """
                {
                  "name": "%s",
                  "maxMember": 2,
                  "deadline": "2026-07-31T23:59:59",
                  "category": "WELFARE",
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
}
