package com.gather.gather.domain.mypage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.mypage.dto.MyPageActivityRecordResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse.CategoryBlock;
import com.gather.gather.domain.mypage.dto.MyPageHomeResponse;
import com.gather.gather.domain.mypage.service.MyPageService;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
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
                                MyPageActivityResponse.ofVolunteer(
                                        volunteerParticipation(), volunteerPosting())));

        mockMvc.perform(get("/api/v1/mypage/activities").param("yearMonth", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].activityType").value("VOLUNTEER"))
                .andExpect(jsonPath("$.data[0].postingId").value(10))
                .andExpect(jsonPath("$.data[0].meetingId").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/mypage/activities returns a MEETING card with meetingId for approved"
                    + " meeting participation")
    void getActivities_returns200WithMeetingCard() throws Exception {
        Meeting meeting =
                Meeting.create(
                        "테스트 모임",
                        "설명",
                        5,
                        LocalDate.of(2026, 6, 1).atStartOfDay(),
                        null,
                        Set.of(PostingCategory.ENVIRONMENT),
                        1L,
                        mockHost(),
                        null,
                        10L,
                        LocalDate.of(2026, 7, 5).atStartOfDay(),
                        LocalDate.of(2026, 7, 6).atStartOfDay());
        ReflectionTestUtils.setField(meeting, "id", 3L);

        when(myPageService.getActivities(eq(YearMonth.of(2026, 7))))
                .thenReturn(
                        List.of(
                                MyPageActivityResponse.ofMeeting(
                                        meeting, PostingParticipationStatus.APPLIED)));

        mockMvc.perform(get("/api/v1/mypage/activities").param("yearMonth", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activityType").value("MEETING"))
                .andExpect(jsonPath("$.data[0].meetingId").value(3))
                .andExpect(jsonPath("$.data[0].postingId").doesNotExist())
                .andExpect(jsonPath("$.data[0].volunteerPostingId").value(10))
                .andExpect(jsonPath("$.data[0].meetingStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data[0].postingParticipationStatus").value("APPLIED"));
    }

    private PostingParticipation volunteerParticipation() {
        PostingParticipation participation = PostingParticipation.create(1L, 10L);
        ReflectionTestUtils.setField(participation, "id", 1L);
        return participation;
    }

    private Posting volunteerPosting() {
        Posting posting =
                Posting.builder()
                        .title("테스트 공고")
                        .status(PostingStatus.RECRUITING)
                        .actStartDate(LocalDate.of(2026, 7, 15))
                        .actEndDate(LocalDate.of(2026, 7, 15))
                        .actStartTime("09:00")
                        .actEndTime("12:00")
                        .actPlace("서울숲공원")
                        .category(PostingCategory.ENVIRONMENT)
                        .build();
        ReflectionTestUtils.setField(posting, "id", 10L);
        return posting;
    }

    private User mockHost() {
        return mock(User.class);
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
                                2,
                                List.of(new CategoryBlock(PostingCategory.ENVIRONMENT, 2)),
                                180,
                                1));

        mockMvc.perform(get("/api/v1/mypage/activities/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCompletedCount").value(2))
                .andExpect(jsonPath("$.data.categoryBlocks[0].category").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.categoryBlocks[0].count").value(2))
                .andExpect(jsonPath("$.data.totalRecognizedMinutes").value(180))
                .andExpect(jsonPath("$.data.timeCertifiableCompletedCount").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities/records returns completed cards")
    void getActivityRecords_returns200WithCards() throws Exception {
        MyPageActivityRecordResponse record =
                new MyPageActivityRecordResponse(
                        1L,
                        10L,
                        "테스트 공고",
                        PostingCategory.ENVIRONMENT,
                        LocalDate.of(2026, 7, 15),
                        LocalDate.of(2026, 7, 15),
                        "서울숲공원",
                        90,
                        true);
        when(myPageService.getActivityRecords(eq(null), any()))
                .thenReturn(
                        PageResponse.from(
                                new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/v1/mypage/activities/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].postingId").value(10))
                .andExpect(jsonPath("$.data.content[0].recognizedMinutes").value(90))
                .andExpect(jsonPath("$.data.content[0].timeCertifiable").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/activities/records filters by category when provided")
    void getActivityRecords_filtersByCategory() throws Exception {
        when(myPageService.getActivityRecords(eq(PostingCategory.EDUCATION), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/api/v1/mypage/activities/records").param("category", "EDUCATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }
}
