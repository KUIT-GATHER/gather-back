package com.gather.gather.domain.user.controller;

import com.gather.gather.domain.user.dto.ProfileImageCurrentResponse;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlRequest;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlResponse;
import com.gather.gather.domain.user.dto.ProfileImageUpdateRequest;
import com.gather.gather.domain.user.dto.ProfileImageUpdateResponse;
import com.gather.gather.domain.user.service.ProfileImageService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Profile Image", description = "사용자 프로필 이미지 업로드·변경 API")
@RestController
@RequestMapping("/api/v1/users/me/profile-image")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    @Operation(summary = "현재 프로필 이미지 조회")
    @GetMapping
    public ApiResponse<ProfileImageCurrentResponse> getCurrentProfileImage() {
        return ApiResponse.success(profileImageService.getCurrentProfileImage());
    }

    @Operation(
            summary = "프로필 이미지 Presigned PUT URL 발급",
            description =
                    """
                    JPEG, PNG, WebP 형식의 최대 5MB 이미지를 업로드할 URL을 발급합니다.
                    프론트는 uploadUrl에 PUT 요청을 보내며, 발급 요청과 동일한 Content-Type과
                    If-None-Match: * 헤더를 사용해야 합니다.
                    업로드 성공 후 응답의 objectKey로 프로필 이미지 반영 API를 호출합니다.
                    """)
    @PostMapping("/presigned-url")
    public ApiResponse<ProfileImagePresignedUrlResponse> createPresignedUrl(
            @Valid @RequestBody ProfileImagePresignedUrlRequest request) {
        return ApiResponse.success(profileImageService.createPresignedUrl(request));
    }

    @Operation(
            summary = "업로드된 프로필 이미지 반영",
            description =
                    """
                    Presigned PUT 업로드가 끝난 객체를 검증한 뒤 현재 사용자의 프로필 이미지로 반영합니다.
                    profileImageUrl은 버킷 정책으로 공개 조회 가능한 URL입니다.
                    """)
    @PatchMapping
    public ApiResponse<ProfileImageUpdateResponse> updateProfileImage(
            @Valid @RequestBody ProfileImageUpdateRequest request) {
        return ApiResponse.success(profileImageService.updateProfileImage(request));
    }
}
