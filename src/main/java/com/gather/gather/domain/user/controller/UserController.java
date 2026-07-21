package com.gather.gather.domain.user.controller;

import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.dto.UserProfileUpdateRequest;
import com.gather.gather.domain.user.service.UserProfileService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "마이페이지 프로필 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserProfileService userProfileService;

    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 마이페이지 프로필을 조회합니다.")
    @GetMapping
    public ApiResponse<UserProfileResponse> getMyProfile() {
        return ApiResponse.success(userProfileService.getMyProfile());
    }

    @Operation(
            summary = "내 프로필 수정",
            description = "회원가입과 동일한 필드 집합을 수정합니다. 프로필 사진은 이 API의 대상이 아니며, 별도의 프로필 사진 API로 관리합니다.")
    @PatchMapping
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(userProfileService.updateMyProfile(request));
    }
}
