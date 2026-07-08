package com.gather.gather.domain.posting.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.dto.PostingLocationResponse;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.service.PostingService;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostingController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PostingService postingService;

    @Test
    @DisplayName("GET /api/v1/postings returns 200 with a page of postings")
    void getPostings_returns200WithPage() throws Exception {
        PostingSummaryResponse summary =
                new PostingSummaryResponse(
                        1L,
                        "동구 환경정화 봉사",
                        PostingStatus.RECRUITING,
                        "울산 동구청",
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 10),
                        "동구 일대",
                        5,
                        1,
                        2L,
                        "동구",
                        10L,
                        "환경");
        when(postingService.getPostings(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(summary), 1, 1, 0, 20));

        mockMvc.perform(get("/api/v1/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].regionName").value("동구"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("환경"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("GET /api/v1/postings returns 200 with empty content when no postings match")
    void getPostings_returns200WithEmptyContent_whenNoPostings() throws Exception {
        when(postingService.getPostings(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/v1/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/postings returns 200 with regionName null when unmatched")
    void getPostings_returns200WithNullRegionName_whenUnmatched() throws Exception {
        PostingSummaryResponse summary =
                new PostingSummaryResponse(
                        1L,
                        "무지역 공고",
                        PostingStatus.RECRUITING,
                        "기관",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        10L,
                        "환경");
        when(postingService.getPostings(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(summary), 1, 1, 0, 20));

        mockMvc.perform(get("/api/v1/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].regionName").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/postings?page=1&size=5 binds Pageable from query params")
    void getPostings_bindsPageableFromQueryParams() throws Exception {
        when(postingService.getPostings(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 1, 5));

        mockMvc.perform(get("/api/v1/postings").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postingService).getPostings(captor.capture(), any(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("GET /api/v1/postings?regionId=1&status=CLOSED binds filter query params")
    void getPostings_bindsFilterQueryParams() throws Exception {
        when(postingService.getPostings(any(), eq(1L), eq(PostingStatus.CLOSED), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0, 20));

        mockMvc.perform(
                        get("/api/v1/postings")
                                .param("regionId", "1")
                                .param("status", "CLOSED")
                                .param("noticeStartDate", "2026-07-01")
                                .param("noticeEndDate", "2026-07-31"))
                .andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        any(),
                        eq(1L),
                        eq(PostingStatus.CLOSED),
                        eq(LocalDate.of(2026, 7, 1)),
                        eq(LocalDate.of(2026, 7, 31)));
    }

    @Test
    @DisplayName("GET /api/v1/postings/{id} returns 200 with detail including locations")
    void getPosting_returns200WithDetail() throws Exception {
        PostingResponse response =
                new PostingResponse(
                        1L,
                        "동구 환경정화 봉사",
                        PostingStatus.RECRUITING,
                        "내용",
                        "울산 동구청",
                        "행복재단",
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 10),
                        "09:00",
                        "18:00",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 9),
                        "월,화",
                        5,
                        1,
                        true,
                        false,
                        false,
                        "동구 일대",
                        "홍길동",
                        "010-0000-0000",
                        "02-000-0000",
                        "test@example.com",
                        "울산 동구 어딘가",
                        2L,
                        "동구",
                        10L,
                        "환경",
                        List.of(new PostingLocationResponse(1, "동구 일대", null, null)),
                        null,
                        null);
        when(postingService.getPosting(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/postings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.regionName").value("동구"))
                .andExpect(jsonPath("$.data.locations").isArray())
                .andExpect(jsonPath("$.data.locations.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/postings/{id} returns 404 when posting does not exist")
    void getPosting_returns404_whenMissing() throws Exception {
        when(postingService.getPosting(999L))
                .thenThrow(new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        mockMvc.perform(get("/api/v1/postings/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/postings/{id} returns 400 when id is not numeric")
    void getPosting_returns400_whenIdNotNumeric() throws Exception {
        mockMvc.perform(get("/api/v1/postings/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
