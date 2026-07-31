package com.gather.gather.domain.badge.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.badge.dto.UserBadgeResponse;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.service.BadgeQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BadgeController.class)
@AutoConfigureMockMvc(addFilters = false)
class BadgeControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BadgeQueryService badgeQueryService;

    @Test
    @DisplayName("GET /api/v1/mypage/badges returns 200 with the caller's earned badges")
    void getMyBadges_returns200WithBadgeList() throws Exception {
        LocalDateTime earnedAt = LocalDateTime.of(2026, 7, 1, 12, 0);
        when(badgeQueryService.getMyBadges())
                .thenReturn(
                        List.of(
                                new UserBadgeResponse(
                                        BadgeType.FIRST_COMPLETION,
                                        BadgeType.FIRST_COMPLETION.getTitle(),
                                        BadgeType.FIRST_COMPLETION.getDescription(),
                                        earnedAt)));

        mockMvc.perform(get("/api/v1/mypage/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].badgeType").value("FIRST_COMPLETION"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName(
            "GET /api/v1/mypage/badges returns 200 with an empty list when the user has no badges (L-10)")
    void getMyBadges_returns200WithEmptyList_whenNoBadges() throws Exception {
        when(badgeQueryService.getMyBadges()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/mypage/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName(
            "GET /api/v1/mypage/badges maps every field and preserves the service's recent-first"
                    + " ordering (L-10)")
    void getMyBadges_mapsAllFieldsAndPreservesRecentFirstOrder() throws Exception {
        LocalDateTime recentlyEarned = LocalDateTime.of(2026, 7, 20, 9, 0);
        LocalDateTime earlierEarned = LocalDateTime.of(2026, 6, 1, 9, 0);
        when(badgeQueryService.getMyBadges())
                .thenReturn(
                        List.of(
                                new UserBadgeResponse(
                                        BadgeType.BOOKMARK_5,
                                        BadgeType.BOOKMARK_5.getTitle(),
                                        BadgeType.BOOKMARK_5.getDescription(),
                                        recentlyEarned),
                                new UserBadgeResponse(
                                        BadgeType.FIRST_COMPLETION,
                                        BadgeType.FIRST_COMPLETION.getTitle(),
                                        BadgeType.FIRST_COMPLETION.getDescription(),
                                        earlierEarned)));

        mockMvc.perform(get("/api/v1/mypage/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].badgeType").value("BOOKMARK_5"))
                .andExpect(jsonPath("$.data[0].title").value(BadgeType.BOOKMARK_5.getTitle()))
                .andExpect(
                        jsonPath("$.data[0].description")
                                .value(BadgeType.BOOKMARK_5.getDescription()))
                .andExpect(jsonPath("$.data[0].earnedAt").value("2026-07-20T09:00:00"))
                .andExpect(jsonPath("$.data[1].badgeType").value("FIRST_COMPLETION"))
                .andExpect(jsonPath("$.data[1].earnedAt").value("2026-06-01T09:00:00"));
    }
}
