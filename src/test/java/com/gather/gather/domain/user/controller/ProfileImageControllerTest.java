package com.gather.gather.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.user.dto.ProfileImageCurrentResponse;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlRequest;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlResponse;
import com.gather.gather.domain.user.dto.ProfileImageUpdateRequest;
import com.gather.gather.domain.user.dto.ProfileImageUpdateResponse;
import com.gather.gather.domain.user.service.ProfileImageService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileImageControllerTest {

    private static final String OBJECT_KEY = "profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProfileImageService profileImageService;

    @Test
    @DisplayName("현재 프로필 이미지 조회 API는 저장된 공개 URL을 반환한다")
    void getCurrentProfileImage_returnsPublicUrl() throws Exception {
        when(profileImageService.getCurrentProfileImage())
                .thenReturn(
                        new ProfileImageCurrentResponse("https://public.example/" + OBJECT_KEY));

        mockMvc.perform(get("/api/v1/users/me/profile-image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.profileImageUrl")
                                .value("https://public.example/" + OBJECT_KEY));
    }

    @Test
    @DisplayName("Presigned URL 발급 API는 공통 응답 형식으로 업로드 정보를 반환한다")
    void createPresignedUrl_returnsUploadInformation() throws Exception {
        when(profileImageService.createPresignedUrl(any(ProfileImagePresignedUrlRequest.class)))
                .thenReturn(
                        new ProfileImagePresignedUrlResponse(
                                "https://presigned.example/upload",
                                OBJECT_KEY,
                                "https://public.example/" + OBJECT_KEY,
                                300));

        mockMvc.perform(
                        post("/api/v1/users/me/profile-image/presigned-url")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "contentType": "image/jpeg",
                                          "fileSize": 1048576
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://presigned.example/upload"))
                .andExpect(jsonPath("$.data.objectKey").value(OBJECT_KEY))
                .andExpect(
                        jsonPath("$.data.publicUrl").value("https://public.example/" + OBJECT_KEY))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("Presigned URL 요청의 파일 크기가 0이면 Bean Validation으로 거부한다")
    void createPresignedUrl_rejectsZeroFileSize() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users/me/profile-image/presigned-url")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "contentType": "image/jpeg",
                                          "fileSize": 0
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(profileImageService);
    }

    @Test
    @DisplayName("프로필 이미지 반영 API는 공개 조회 URL을 반환한다")
    void updateProfileImage_returnsPublicUrl() throws Exception {
        when(profileImageService.updateProfileImage(any(ProfileImageUpdateRequest.class)))
                .thenReturn(new ProfileImageUpdateResponse("https://public.example/" + OBJECT_KEY));

        mockMvc.perform(
                        patch("/api/v1/users/me/profile-image")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "objectKey": "%s"
                                        }
                                        """
                                                .formatted(OBJECT_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.profileImageUrl")
                                .value("https://public.example/" + OBJECT_KEY))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("빈 objectKey는 Bean Validation으로 거부한다")
    void updateProfileImage_rejectsBlankObjectKey() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me/profile-image")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "objectKey": " "
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(profileImageService);
    }

    @Test
    @DisplayName("저장소 객체 부재 오류는 내부 AWS 메시지 없이 404 공통 응답으로 반환한다")
    void updateProfileImage_returnsSafeNotFoundResponse() throws Exception {
        when(profileImageService.updateProfileImage(any(ProfileImageUpdateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PROFILE_IMAGE_OBJECT_NOT_FOUND));

        mockMvc.perform(
                        patch("/api/v1/users/me/profile-image")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "objectKey": "%s"
                                        }
                                        """
                                                .formatted(OBJECT_KEY)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("PROFILE_IMAGE_OBJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("업로드된 프로필 이미지 객체를 찾을 수 없습니다."));
    }
}
