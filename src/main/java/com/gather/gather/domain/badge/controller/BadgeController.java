package com.gather.gather.domain.badge.controller;

import com.gather.gather.domain.badge.dto.UserBadgeResponse;
import com.gather.gather.domain.badge.service.BadgeQueryService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Badge", description = "뱃지 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage/badges")
public class BadgeController {

    private final BadgeQueryService badgeQueryService;

    @Operation(summary = "내 뱃지 목록 조회", description = "로그인한 사용자가 획득한 뱃지 목록을 최근 획득순으로 조회합니다.")
    @GetMapping
    public ApiResponse<List<UserBadgeResponse>> getMyBadges() {
        return ApiResponse.success(badgeQueryService.getMyBadges());
    }
}
