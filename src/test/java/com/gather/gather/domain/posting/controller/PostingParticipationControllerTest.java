package com.gather.gather.domain.posting.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.dto.PostingParticipationApplyRequest;
import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.service.PostingParticipationService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostingParticipationController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostingParticipationControllerTest {

    private static final LocalDate PARTICIPATION_START_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalDate PARTICIPATION_END_DATE = LocalDate.of(2026, 8, 18);
    private static final String VALID_REQUEST_BODY =
            """
            {"participationStartDate":"2026-08-15","participationEndDate":"2026-08-18"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PostingParticipationService postingParticipationService;

    @Test
    @DisplayName("POST /api/v1/postings/{id}/participations returns 200 with participation details")
    void apply_returns200WithParticipationDetails() throws Exception {
        PostingParticipation participation =
                PostingParticipation.create(
                        1L, 1L, PARTICIPATION_START_DATE, PARTICIPATION_END_DATE);
        ReflectionTestUtils.setField(participation, "id", 1L);
        when(postingParticipationService.apply(1L, expectedRequest()))
                .thenReturn(PostingParticipationResponse.of(participation));

        mockMvc.perform(
                        post("/api/v1/postings/1/participations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participationId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.participationStartDate").value("2026-08-15"))
                .andExpect(jsonPath("$.data.participationEndDate").value("2026-08-18"));
    }

    @Test
    @DisplayName(
            "POST /api/v1/postings/{id}/participations returns 404 when the posting does not"
                    + " exist")
    void apply_returns404_whenPostingMissing() throws Exception {
        when(postingParticipationService.apply(999L, expectedRequest()))
                .thenThrow(new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        mockMvc.perform(
                        post("/api/v1/postings/999/participations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/postings/{id}/participations returns 409 when already applied")
    void apply_returns409_whenDuplicate() throws Exception {
        when(postingParticipationService.apply(1L, expectedRequest()))
                .thenThrow(new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE));

        mockMvc.perform(
                        post("/api/v1/postings/1/participations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PARTICIPATION_DUPLICATE"));
    }

    @Test
    @DisplayName("POST /api/v1/postings/{id}/participations returns 409 when the posting is closed")
    void apply_returns409_whenPostingClosed() throws Exception {
        when(postingParticipationService.apply(1L, expectedRequest()))
                .thenThrow(new BusinessException(ErrorCode.POSTING_CLOSED));

        mockMvc.perform(
                        post("/api/v1/postings/1/participations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POSTING_CLOSED"));
    }

    @Test
    @DisplayName(
            "POST /api/v1/postings/{id}/participations returns 409 when the participation date"
                    + " is outside the posting period")
    void apply_returns409_whenDateOutOfPostingPeriod() throws Exception {
        when(postingParticipationService.apply(1L, expectedRequest()))
                .thenThrow(
                        new BusinessException(ErrorCode.PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD));

        mockMvc.perform(
                        post("/api/v1/postings/1/participations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(
                        jsonPath("$.error.code").value("PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD"));
    }

    @Test
    @DisplayName(
            "POST /api/v1/postings/{id}/participations returns 400 when postingId is not"
                    + " numeric")
    void apply_returns400_whenPostingIdNotNumeric() throws Exception {
        mockMvc.perform(
                        post("/api/v1/postings/abc/participations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName(
            "POST /api/v1/postings/{id}/participations returns 400 when the request body is"
                    + " missing required dates")
    void apply_returns400_whenRequestBodyMissingDates() throws Exception {
        mockMvc.perform(
                        post("/api/v1/postings/1/participations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/postings/{id}/participations returns 200 on cancel")
    void cancel_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/postings/1/participations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postingParticipationService).cancel(1L);
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/postings/{id}/participations returns 404 when no participation exists")
    void cancel_returns404_whenParticipationMissing() throws Exception {
        doThrow(new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND))
                .when(postingParticipationService)
                .cancel(999L);

        mockMvc.perform(delete("/api/v1/postings/999/participations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PARTICIPATION_NOT_FOUND"));
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/postings/{id}/participations returns 409 when cancel is not allowed"
                    + " for the participation's status")
    void cancel_returns409_whenCancelNotAllowed() throws Exception {
        doThrow(new BusinessException(ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED))
                .when(postingParticipationService)
                .cancel(1L);

        mockMvc.perform(delete("/api/v1/postings/1/participations"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PARTICIPATION_CANCEL_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/postings/{id}/participations/complete returns 200 on completion")
    void complete_returns200() throws Exception {
        mockMvc.perform(patch("/api/v1/postings/1/participations/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postingParticipationService).complete(1L);
    }

    @Test
    @DisplayName(
            "PATCH /api/v1/postings/{id}/participations/complete returns 409 when the activity"
                    + " has not ended yet")
    void complete_returns409_whenNotEnded() throws Exception {
        doThrow(new BusinessException(ErrorCode.PARTICIPATION_COMPLETE_NOT_ALLOWED))
                .when(postingParticipationService)
                .complete(1L);

        mockMvc.perform(patch("/api/v1/postings/1/participations/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PARTICIPATION_COMPLETE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/postings/{id}/participations/hours returns 200 on submission")
    void submitRecognizedMinutes_returns200() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/postings/1/participations/hours")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recognizedMinutes\": 210}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postingParticipationService).submitRecognizedMinutes(1L, 210);
    }

    @Test
    @DisplayName(
            "PATCH /api/v1/postings/{id}/participations/hours returns 409 when the participation"
                    + " is not completed yet")
    void submitRecognizedMinutes_returns409_whenNotCompleted() throws Exception {
        doThrow(new BusinessException(ErrorCode.PARTICIPATION_HOURS_NOT_ALLOWED))
                .when(postingParticipationService)
                .submitRecognizedMinutes(1L, 210);

        mockMvc.perform(
                        patch("/api/v1/postings/1/participations/hours")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recognizedMinutes\": 210}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PARTICIPATION_HOURS_NOT_ALLOWED"));
    }

    private PostingParticipationApplyRequest expectedRequest() {
        return new PostingParticipationApplyRequest(
                PARTICIPATION_START_DATE, PARTICIPATION_END_DATE);
    }
}
