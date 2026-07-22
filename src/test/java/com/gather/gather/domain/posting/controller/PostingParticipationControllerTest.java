package com.gather.gather.domain.posting.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.service.PostingParticipationService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostingParticipationController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostingParticipationControllerTest {

    private static final String APPLICATION_URL =
            "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=3422497";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PostingParticipationService postingParticipationService;

    @Test
    @DisplayName("POST /api/v1/postings/{id}/participations returns 200 with participation details")
    void apply_returns200WithParticipationDetails() throws Exception {
        when(postingParticipationService.apply(1L))
                .thenReturn(
                        PostingParticipationResponse.of(
                                1L, PostingParticipationStatus.APPLIED, APPLICATION_URL));

        mockMvc.perform(post("/api/v1/postings/1/participations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participationId").value(1))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.applicationUrl").value(APPLICATION_URL));
    }

    @Test
    @DisplayName(
            "POST /api/v1/postings/{id}/participations returns 404 when the posting does not"
                    + " exist")
    void apply_returns404_whenPostingMissing() throws Exception {
        when(postingParticipationService.apply(999L))
                .thenThrow(new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        mockMvc.perform(post("/api/v1/postings/999/participations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/postings/{id}/participations returns 409 when already applied")
    void apply_returns409_whenDuplicate() throws Exception {
        when(postingParticipationService.apply(1L))
                .thenThrow(new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE));

        mockMvc.perform(post("/api/v1/postings/1/participations"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PARTICIPATION_DUPLICATE"));
    }

    @Test
    @DisplayName(
            "POST /api/v1/postings/{id}/participations returns 400 when postingId is not"
                    + " numeric")
    void apply_returns400_whenPostingIdNotNumeric() throws Exception {
        mockMvc.perform(post("/api/v1/postings/abc/participations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
