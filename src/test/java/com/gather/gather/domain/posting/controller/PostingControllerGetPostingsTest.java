package com.gather.gather.domain.posting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.dto.PostingListItem;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.service.PostingKeywordRecommendationService;
import com.gather.gather.domain.posting.service.PostingRecommendationService;
import com.gather.gather.domain.posting.service.PostingService;
import com.gather.gather.global.common.CursorPageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/postings(무한스크롤용 커서 페이지네이션) 전용 컨트롤러 테스트.
 *
 * <p>기존 {@code PostingControllerTest}의 getPostings 관련 테스트는 {@code PostingService#getPostings}가
 * {@code Pageable} 대신 {@code Sort/cursor/size}를 받도록 바뀌면서 더 이상 컴파일되지 않는다. 그 테스트들을 대체하는 파일이며, {@code
 * getPosting}/{@code getPostingsMap}/{@code getRecommendedPostings}/{@code getRecommendedKeywords}
 * 등 다른 엔드포인트 테스트는 기존 파일에 그대로 둔다(이 파일은 목록 조회만 다룬다).
 */
@WebMvcTest(PostingController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostingControllerGetPostingsTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PostingService postingService;
    @MockitoBean private PostingKeywordRecommendationService postingKeywordRecommendationService;
    @MockitoBean private PostingRecommendationService postingRecommendationService;

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "id");

    private CursorPageResponse<PostingListItem> emptyResponse() {
        return new CursorPageResponse<>(List.of(), null, false);
    }

    @Test
    @DisplayName("GET /api/v1/postings returns 200 with a page of postings")
    void getPostings_returns200WithPage() throws Exception {
        PostingListItem item =
                new PostingListItem(
                        com.gather.gather.domain.posting.dto.PostingSourceType.POSTING,
                        1L,
                        null,
                        "동구 환경정화 봉사",
                        "울산 동구청",
                        null,
                        2L,
                        "동구",
                        "동구 일대",
                        java.time.LocalDateTime.of(2026, 7, 10, 9, 0),
                        java.time.LocalDateTime.of(2026, 7, 10, 18, 0),
                        java.time.LocalDateTime.of(2026, 7, 9, 23, 59),
                        5,
                        1,
                        List.of(PostingCategory.ENVIRONMENT),
                        "RECRUITING");
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(new CursorPageResponse<>(List.of(item), "next-cursor-token", true));

        mockMvc.perform(get("/api/v1/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].sourceType").value("POSTING"))
                .andExpect(jsonPath("$.data.content[0].regionName").value("동구"))
                .andExpect(jsonPath("$.data.content[0].categories[0]").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor-token"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/postings returns 200 with empty content when no postings match")
    void getPostings_returns200WithEmptyContent_whenNoPostings() throws Exception {
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/postings?cursor=... binds the cursor query param through unchanged")
    void getPostings_bindsCursorQueryParam() throws Exception {
        when(postingService.getPostings(
                        any(),
                        eq("prev-cursor"),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings").param("cursor", "prev-cursor"))
                .andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        any(),
                        eq("prev-cursor"),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    @DisplayName("GET /api/v1/postings?size=5 binds the size query param through unchanged")
    void getPostings_bindsSizeQueryParam() throws Exception {
        when(postingService.getPostings(
                        any(), any(), eq(5), any(), any(), any(), any(), any(), any(), any(), any(),
                        any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings").param("size", "5")).andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        any(), any(), eq(5), any(), any(), any(), any(), any(), any(), any(), any(),
                        any());
    }

    @Test
    @DisplayName("GET /api/v1/postings?sort=applyDeadlineAt,asc binds the sort query param")
    void getPostings_bindsSortQueryParam() throws Exception {
        Sort applyDeadlineAsc = Sort.by(Sort.Direction.ASC, "applyDeadlineAt");
        when(postingService.getPostings(
                        eq(applyDeadlineAsc),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings").param("sort", "applyDeadlineAt,asc"))
                .andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        eq(applyDeadlineAsc),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    @DisplayName("GET /api/v1/postings defaults sort to id,desc when not specified")
    void getPostings_defaultsSortToIdDesc_whenNotSpecified() throws Exception {
        when(postingService.getPostings(
                        eq(DEFAULT_SORT),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings")).andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        eq(DEFAULT_SORT),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    @DisplayName("GET /api/v1/postings?keyword=환경 binds keyword query param")
    void getPostings_bindsKeywordQueryParam() throws Exception {
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq("환경"),
                        any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings").param("keyword", "환경")).andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq("환경"),
                        any());
    }

    @Test
    @DisplayName("GET /api/v1/postings?category=WELFARE binds category query param")
    void getPostings_bindsCategoryQueryParam() throws Exception {
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(PostingCategory.WELFARE)))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings").param("category", "WELFARE"))
                .andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(PostingCategory.WELFARE));
    }

    @Test
    @DisplayName("GET /api/v1/postings?regionId=1&status=CLOSED binds filter query params")
    void getPostings_bindsFilterQueryParams() throws Exception {
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        eq(1L),
                        any(),
                        eq(PostingStatus.CLOSED),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(emptyResponse());

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
                        any(),
                        any(Integer.class),
                        eq(1L),
                        any(),
                        eq(PostingStatus.CLOSED),
                        eq(LocalDate.of(2026, 7, 1)),
                        eq(LocalDate.of(2026, 7, 31)),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    @DisplayName("GET /api/v1/postings?regionGroupId=7 binds region group query param")
    void getPostings_bindsRegionGroupIdQueryParam() throws Exception {
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        eq(7L),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/postings").param("regionGroupId", "7"))
                .andExpect(status().isOk());

        verify(postingService)
                .getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        eq(7L),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    @DisplayName(
            "GET /api/v1/postings?regionId=1&regionGroupId=7 returns 400 when service rejects the"
                    + " combination")
    void getPostings_returns400_whenRegionIdAndRegionGroupIdBothProvided() throws Exception {
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        eq(1L),
                        eq(7L),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/api/v1/postings").param("regionId", "1").param("regionGroupId", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/postings?sort=invalidProp returns 400 when sort property is not"
                    + " whitelisted")
    void getPostings_returns400_whenSortPropertyInvalid() throws Exception {
        when(postingService.getPostings(
                        any(),
                        any(),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/api/v1/postings").param("sort", "invalidProp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/postings returns 400 when the cursor is invalid or stale")
    void getPostings_returns400_whenCursorInvalid() throws Exception {
        when(postingService.getPostings(
                        any(),
                        eq("broken-cursor"),
                        any(Integer.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/api/v1/postings").param("cursor", "broken-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
