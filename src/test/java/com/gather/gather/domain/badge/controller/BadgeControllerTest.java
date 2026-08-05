package com.gather.gather.domain.badge.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.badge.dto.BadgeStatusResponse;
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
    @DisplayName(
            "GET /api/v1/mypage/badges returns 200 with an earned badge card showing earnedAt"
                    + " and currentValue=targetValue")
    void getMyBadges_returns200WithEarnedBadge() throws Exception {
        LocalDateTime earnedAt = LocalDateTime.of(2026, 7, 1, 12, 0);
        when(badgeQueryService.getMyBadges())
                .thenReturn(
                        List.of(
                                new BadgeStatusResponse(
                                        BadgeType.FIRST_COMPLETION,
                                        BadgeType.FIRST_COMPLETION.getTitle(),
                                        BadgeType.FIRST_COMPLETION.getDescription(),
                                        true,
                                        earnedAt,
                                        1,
                                        1)));

        mockMvc.perform(get("/api/v1/mypage/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].badgeType").value("FIRST_COMPLETION"))
                .andExpect(jsonPath("$.data[0].earned").value(true))
                .andExpect(jsonPath("$.data[0].earnedAt").value("2026-07-01T12:00:00"))
                .andExpect(jsonPath("$.data[0].currentValue").value(1))
                .andExpect(jsonPath("$.data[0].targetValue").value(1))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName(
            "GET /api/v1/mypage/badges returns 200 with a locked badge card showing null"
                    + " earnedAt and progress toward targetValue")
    void getMyBadges_returns200WithLockedBadge() throws Exception {
        when(badgeQueryService.getMyBadges())
                .thenReturn(
                        List.of(
                                new BadgeStatusResponse(
                                        BadgeType.COMPLETION_5,
                                        BadgeType.COMPLETION_5.getTitle(),
                                        BadgeType.COMPLETION_5.getDescription(),
                                        false,
                                        null,
                                        3,
                                        5)));

        mockMvc.perform(get("/api/v1/mypage/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].badgeType").value("COMPLETION_5"))
                .andExpect(jsonPath("$.data[0].earned").value(false))
                .andExpect(jsonPath("$.data[0].earnedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].currentValue").value(3))
                .andExpect(jsonPath("$.data[0].targetValue").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/badges returns all 8 badge types even when none are earned")
    void getMyBadges_returns200WithAllEightBadgeTypes_whenNoneEarned() throws Exception {
        List<BadgeStatusResponse> allLocked =
                java.util.Arrays.stream(BadgeType.values())
                        .map(
                                badgeType ->
                                        new BadgeStatusResponse(
                                                badgeType,
                                                badgeType.getTitle(),
                                                badgeType.getDescription(),
                                                false,
                                                null,
                                                0,
                                                badgeType.getTargetValue()))
                        .toList();
        when(badgeQueryService.getMyBadges()).thenReturn(allLocked);

        mockMvc.perform(get("/api/v1/mypage/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(BadgeType.values().length));
    }
}
