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
import com.gather.gather.domain.posting.dto.PostingParticipationAction;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.service.PostingKeywordRecommendationService;
import com.gather.gather.domain.posting.service.PostingRecommendationService;
import com.gather.gather.domain.posting.service.PostingService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/postings(목록 조회, 무한스크롤 커서 페이지네이션) 테스트는 {@link PostingControllerGetPostingsTest}로 분리했다
 * — {@code PostingService#getPostings}가 {@code Pageable} 대신 {@code Sort/cursor/size}를 받도록 바뀌면서 이
 * 파일에 있던 관련 테스트들은 삭제했다. 지도 조회(getPostingsMap)·상세 조회(getPosting)·추천/키워드 엔드포인트는 이번 변경과 무관해 그대로 유지한다.
 */
@WebMvcTest(PostingController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PostingService postingService;

    @MockitoBean private PostingKeywordRecommendationService postingKeywordRecommendationService;

    @MockitoBean private PostingRecommendationService postingRecommendationService;

    @Test
    @DisplayName("GET /api/v1/postings/map returns 200 with matching postings")
    void getPostingsMap_returns200WithMatchingPostings() throws Exception {
        com.gather.gather.domain.posting.dto.PostingMapItem item =
                new com.gather.gather.domain.posting.dto.PostingMapItem(
                        1L,
                        "독거어르신 도시락 배달",
                        "OO복지관",
                        10L,
                        "양천구",
                        LocalDateTime.of(2026, 8, 20, 0, 0),
                        LocalDateTime.of(2026, 8, 25, 0, 0),
                        LocalDateTime.of(2026, 8, 18, 0, 0),
                        PostingCategory.WELFARE,
                        PostingStatus.RECRUITING,
                        List.of(
                                new PostingLocationResponse(
                                        1,
                                        "서울특별시 양천구",
                                        java.math.BigDecimal.valueOf(37.5251621),
                                        java.math.BigDecimal.valueOf(126.8560855))));
        when(postingService.getPostingsMap(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(item));

        mockMvc.perform(
                        get("/api/v1/postings/map")
                                .param("swLat", "37.50")
                                .param("swLng", "126.80")
                                .param("neLat", "37.60")
                                .param("neLng", "126.95"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].regionName").value("양천구"))
                .andExpect(jsonPath("$.data[0].locations[0].locationSeq").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/postings/map returns 400 when bounds params are missing")
    void getPostingsMap_returns400_whenBoundsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/postings/map"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/postings/map binds regionId/activity date/category query params")
    void getPostingsMap_bindsFilterQueryParams() throws Exception {
        when(postingService.getPostingsMap(
                        eq(1L),
                        eq(LocalDate.of(2026, 8, 20)),
                        eq(LocalDate.of(2026, 8, 25)),
                        eq(PostingCategory.WELFARE),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/v1/postings/map")
                                .param("regionId", "1")
                                .param("activityStartDate", "2026-08-20")
                                .param("activityEndDate", "2026-08-25")
                                .param("category", "WELFARE")
                                .param("swLat", "37.50")
                                .param("swLng", "126.80")
                                .param("neLat", "37.60")
                                .param("neLng", "126.95"))
                .andExpect(status().isOk());

        verify(postingService)
                .getPostingsMap(
                        eq(1L),
                        eq(LocalDate.of(2026, 8, 20)),
                        eq(LocalDate.of(2026, 8, 25)),
                        eq(PostingCategory.WELFARE),
                        any(),
                        any(),
                        any(),
                        any());
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
                        PostingCategory.ENVIRONMENT,
                        List.of(new PostingLocationResponse(1, "동구 일대", null, null)),
                        null,
                        null,
                        true,
                        PostingParticipationStatus.APPLIED,
                        PostingParticipationAction.CANCEL,
                        PostingSource.API_1365,
                        "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=3422497",
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 10));
        when(postingService.getPosting(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/postings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.regionName").value("동구"))
                .andExpect(jsonPath("$.data.locations").isArray())
                .andExpect(jsonPath("$.data.locations.length()").value(1))
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andExpect(jsonPath("$.data.participationStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.participationAction").value("CANCEL"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/postings/{id} returns participationStatus null and participationAction"
                    + " APPLY when anonymous or not participating")
    void getPosting_returns200WithNullParticipationStatus_whenAnonymousOrNotParticipating()
            throws Exception {
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
                        PostingCategory.ENVIRONMENT,
                        List.of(new PostingLocationResponse(1, "동구 일대", null, null)),
                        null,
                        null,
                        false,
                        null,
                        PostingParticipationAction.APPLY,
                        PostingSource.API_1365,
                        "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=3422497",
                        null,
                        null);
        when(postingService.getPosting(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/postings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participationStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.participationAction").value("APPLY"));
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

    @Test
    @DisplayName("GET /api/v1/postings/keywords/recommended returns 200 with top keywords")
    void getRecommendedKeywords_returns200WithKeywords() throws Exception {
        when(postingKeywordRecommendationService.getRecommendedKeywords())
                .thenReturn(List.of("유기견", "봉사", "환경정화"));

        mockMvc.perform(get("/api/v1/postings/keywords/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0]").value("유기견"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/postings/keywords/recommended returns 200 with empty list when no data")
    void getRecommendedKeywords_returns200WithEmptyList_whenNoData() throws Exception {
        when(postingKeywordRecommendationService.getRecommendedKeywords()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/postings/keywords/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/postings/recommended returns 200 with recommended postings")
    void getRecommendedPostings_returns200WithRecommendations() throws Exception {
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
                        PostingCategory.ENVIRONMENT,
                        LocalDate.of(2026, 7, 5));
        when(postingRecommendationService.getRecommendedPostings()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/postings/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].category").value("ENVIRONMENT"));

        verify(postingRecommendationService).getRecommendedPostings();
    }

    @Test
    @DisplayName("GET /api/v1/postings/recommended returns 200 with empty list when no data")
    void getRecommendedPostings_returns200WithEmptyList_whenNoData() throws Exception {
        when(postingRecommendationService.getRecommendedPostings()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/postings/recommended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
