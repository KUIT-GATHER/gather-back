package com.gather.gather.domain.badge.controller;

import com.gather.gather.domain.badge.dto.BadgeStatusResponse;
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

    @Operation(
            summary = "내 뱃지 목록 조회",
            description = "잠긴 뱃지를 포함한 전체 8종 뱃지의 현재 상태(획득 여부, 진행 수치)를 조회합니다.")
    @GetMapping
    public ApiResponse<List<BadgeStatusResponse>> getMyBadges() {
        return ApiResponse.success(badgeQueryService.getMyBadges());
    }
}
