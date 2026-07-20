package com.gather.gather.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.user.dto.ProfileImageUploadUrlResponse;
import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.service.UserProfileService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserProfileService userProfileService;

    @Test
    @DisplayName("GET /api/v1/users/me returns the current user's profile")
    void getMyProfile_returns200WithProfile() throws Exception {
        when(userProfileService.getMyProfile()).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("길동"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/img.jpg"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me updates the profile and returns 200")
    void updateMyProfile_returns200_whenValid() throws Exception {
        when(userProfileService.updateMyProfile(any())).thenReturn(sampleProfile());

        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "nickname": "길동",
                                          "introduction": "소개글",
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "profileImageKey": null
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("길동"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me returns 409 when the nickname is already taken")
    void updateMyProfile_returns409_whenNicknameDuplicate() throws Exception {
        when(userProfileService.updateMyProfile(any()))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_NICKNAME));

        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "nickname": "이미있는닉네임",
                                          "introduction": null,
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "profileImageKey": null
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me returns 400 when required fields are missing")
    void updateMyProfile_returns400_whenValidationFails() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "",
                                          "nickname": "길동",
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"]
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/profile-image/upload-url returns a mock presigned URL")
    void createProfileImageUploadUrl_returns200WithMockUrl() throws Exception {
        when(userProfileService.createProfileImageUploadUrl(any()))
                .thenReturn(
                        new ProfileImageUploadUrlResponse(
                                "https://mock-upload-url", "profiles/1/uuid.jpg", 300L));

        mockMvc.perform(
                        post("/api/v1/users/me/profile-image/upload-url")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "fileExtension": "jpg",
                                          "contentType": "image/jpeg"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.objectKey").value("profiles/1/uuid.jpg"));
    }

    @Test
    @DisplayName(
            "POST /api/v1/users/me/profile-image/upload-url returns 400 for a disallowed"
                    + " extension")
    void createProfileImageUploadUrl_returns400_whenExtensionNotAllowed() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users/me/profile-image/upload-url")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "fileExtension": "exe",
                                          "contentType": "image/jpeg"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private UserProfileResponse sampleProfile() {
        return new UserProfileResponse(
                1L,
                "홍길동",
                "길동",
                "소개글",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                new RegionResponse(123L, "강남구", 2, "11680", null, null),
                List.of(PostingCategory.WELFARE),
                "https://example.com/img.jpg");
    }
}
