package com.gather.gather.domain.posting.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.service.PostingService;
import com.gather.gather.global.common.PageResponse;
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
        when(postingService.getPostings(any()))
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
        when(postingService.getPostings(any()))
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
        when(postingService.getPostings(any()))
                .thenReturn(new PageResponse<>(List.of(summary), 1, 1, 0, 20));

        mockMvc.perform(get("/api/v1/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].regionName").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/postings?page=1&size=5 binds Pageable from query params")
    void getPostings_bindsPageableFromQueryParams() throws Exception {
        when(postingService.getPostings(any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 1, 5));

        mockMvc.perform(get("/api/v1/postings").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postingService).getPostings(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }
}
