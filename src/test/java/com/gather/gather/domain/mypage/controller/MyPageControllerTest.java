package com.gather.gather.domain.mypage.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.mypage.dto.MyPageActivityRecordResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse.CategoryBlock;
import com.gather.gather.domain.mypage.dto.MyPageHomeResponse;
import com.gather.gather.domain.mypage.service.MyPageService;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyPageController.class)
@AutoConfigureMockMvc(addFilters = false)
class MyPageControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MyPageService myPageService;

    @Test
    @DisplayName("GET /api/v1/mypage/home returns the profile summary and bookmark flag")
    void getHome_returns200WithHomeSummary() throws Exception {
        RegionResponse region = new RegionResponse(1L, "강남구", 2, "11680", null, null);
        when(myPageService.getHome())
                .thenReturn(
                        new MyPageHomeResponse(
                                "길동",
                                "https://cdn.example.com/profiles/1.png",
                                LocalDate.of(2000, 1, 1),
                                region,
                                true));

        mockMvc.perform(get("/api/v1/mypage/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("길동"))
                .andExpect(jsonPath("$.data.hasBookmark").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/home returns 404 when the user no longer exists")
    void getHome_returns404_whenUserMissing() throws Exception {
        when(myPageService.getHome()).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/mypage/home"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities returns the month's activity cards")
    void getActivities_returns200WithCards() throws Exception {
        when(myPageService.getActivities(eq(YearMonth.of(2026, 7))))
                .thenReturn(
                        List.of(
                                new MyPageActivityResponse(
                                        1L,
                                        10L,
                                        "테스트 공고",
                                        LocalDate.of(2026, 7, 15),
                                        LocalDate.of(2026, 7, 15),
                                        "09:00",
                                        "12:00",
                                        "서울숲공원",
                                        PostingParticipationStatus.APPLIED)));

        mockMvc.perform(get("/api/v1/mypage/activities").param("yearMonth", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].postingId").value(10))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities returns 400 when yearMonth is missing")
    void getActivities_returns400_whenYearMonthMissing() throws Exception {
        mockMvc.perform(get("/api/v1/mypage/activities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities returns 400 when yearMonth has the wrong format")
    void getActivities_returns400_whenYearMonthMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/mypage/activities").param("yearMonth", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities/summary returns total count and category blocks")
    void getActivitySummary_returns200WithSummary() throws Exception {
        when(myPageService.getActivitySummary())
                .thenReturn(
                        MyPageActivitySummaryResponse.of(
                                2, List.of(new CategoryBlock(PostingCategory.ENVIRONMENT, 2))));

        mockMvc.perform(get("/api/v1/mypage/activities/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCompletedCount").value(2))
                .andExpect(jsonPath("$.data.categoryBlocks[0].category").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.categoryBlocks[0].count").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities/records returns completed cards")
    void getActivityRecords_returns200WithCards() throws Exception {
        when(myPageService.getActivityRecords(null))
                .thenReturn(
                        List.of(
                                new MyPageActivityRecordResponse(
                                        1L,
                                        10L,
                                        "테스트 공고",
                                        PostingCategory.ENVIRONMENT,
                                        LocalDate.of(2026, 7, 15),
                                        LocalDate.of(2026, 7, 15),
                                        "서울숲공원",
                                        90)));

        mockMvc.perform(get("/api/v1/mypage/activities/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].postingId").value(10))
                .andExpect(jsonPath("$.data[0].recognizedMinutes").value(90));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities/records filters by category when provided")
    void getActivityRecords_filtersByCategory() throws Exception {
        when(myPageService.getActivityRecords(PostingCategory.EDUCATION)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/mypage/activities/records").param("category", "EDUCATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
