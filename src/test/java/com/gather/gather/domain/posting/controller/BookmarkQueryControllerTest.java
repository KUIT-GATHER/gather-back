package com.gather.gather.domain.posting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.service.BookmarkService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookmarkQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookmarkQueryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BookmarkService bookmarkService;

    @Test
    @DisplayName("GET /api/v1/postings/bookmarks returns 200 with bookmarked postings")
    void getBookmarkedPostings_returns200WithContent() throws Exception {
        PostingSummaryResponse posting =
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
                        PostingCategory.ENVIRONMENT,
                        LocalDate.of(2026, 7, 5));
        Page<PostingSummaryResponse> page =
                new PageImpl<>(List.of(posting), PageRequest.of(0, 20), 1);
        when(bookmarkService.getBookmarkedPostings(
                        isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(PageResponse.from(page));

        mockMvc.perform(get("/api/v1/postings/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].regionName").value("동구"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/postings/bookmarks passes category and keyword query params through")
    void getBookmarkedPostings_passesCategoryAndKeyword() throws Exception {
        Page<PostingSummaryResponse> emptyPage =
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(bookmarkService.getBookmarkedPostings(
                        eq(PostingCategory.ENVIRONMENT),
                        eq("정화"),
                        isNull(),
                        isNull(),
                        isNull(),
                        any()))
                .thenReturn(PageResponse.from(emptyPage));

        mockMvc.perform(
                        get("/api/v1/postings/bookmarks")
                                .param("category", "ENVIRONMENT")
                                .param("keyword", "정화"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/postings/bookmarks returns 401 when not authenticated")
    void getBookmarkedPostings_returns401_whenUnauthenticated() throws Exception {
        when(bookmarkService.getBookmarkedPostings(
                        isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/postings/bookmarks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/postings/bookmarks?page=1&size=5 binds page/size query params")
    void getBookmarkedPostings_bindsPageableFromQueryParams() throws Exception {
        Page<PostingSummaryResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(1, 5), 0);
        when(bookmarkService.getBookmarkedPostings(
                        isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(PageResponse.from(emptyPage));

        mockMvc.perform(get("/api/v1/postings/bookmarks").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookmarkService)
                .getBookmarkedPostings(
                        isNull(), isNull(), isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName(
            "GET /api/v1/postings/bookmarks?sort=... returns 400 when the service rejects sort")
    void getBookmarkedPostings_returns400_whenServiceRejectsSort() throws Exception {
        when(bookmarkService.getBookmarkedPostings(
                        isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/api/v1/postings/bookmarks").param("sort", "title,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
