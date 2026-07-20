package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.user.dto.ProfileImageUploadUrlRequest;
import com.gather.gather.domain.user.dto.ProfileImageUploadUrlResponse;
import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.dto.UserProfileUpdateRequest;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 123L;

    @Mock private UserRepository userRepository;
    @Mock private SignupValidator signupValidator;
    @Mock private ProfileImageStorageClient profileImageStorageClient;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService =
                new UserProfileService(userRepository, signupValidator, profileImageStorageClient);
    }

    @Test
    @DisplayName("getMyProfile returns the current user's profile with an assembled image URL")
    void getMyProfile_returnsProfile_withImageUrl() {
        User user = existingUser();
        ReflectionTestUtils.setField(user, "profileImageKey", "profiles/1/old.jpg");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(profileImageStorageClient.buildPublicUrl("profiles/1/old.jpg"))
                    .thenReturn("https://bucket.s3.region.amazonaws.com/profiles/1/old.jpg");

            UserProfileResponse response = userProfileService.getMyProfile();

            assertThat(response.nickname()).isEqualTo("길동");
            assertThat(response.profileImageUrl())
                    .isEqualTo("https://bucket.s3.region.amazonaws.com/profiles/1/old.jpg");
        }
    }

    @Test
    @DisplayName("getMyProfile returns a null image URL when no profile image is set")
    void getMyProfile_returnsNullImageUrl_whenNoImageKey() {
        User user = existingUser();

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserProfileResponse response = userProfileService.getMyProfile();

            assertThat(response.profileImageUrl()).isNull();
            verify(profileImageStorageClient, never()).buildPublicUrl(any());
        }
    }

    @Test
    @DisplayName("getMyProfile throws USER_NOT_FOUND when the user no longer exists")
    void getMyProfile_throwsUserNotFound_whenMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.getMyProfile())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile updates fields and skips the nickname duplicate check when unchanged")
    void updateMyProfile_updatesFields_whenNicknameUnchanged() {
        User user = existingUser();
        Region newRegion = Region.create("종로구", 2, "11110", null);
        UserProfileUpdateRequest request = updateRequest("길동", null);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(signupValidator.findActivityRegion(REGION_ID)).thenReturn(newRegion);
            when(signupValidator.normalizeNullableText(any())).thenReturn("소개글");
            when(userRepository.saveAndFlush(user)).thenReturn(user);

            UserProfileResponse response = userProfileService.updateMyProfile(request);

            assertThat(response.name()).isEqualTo("홍길동");
            assertThat(user.getActivityRegion()).isSameAs(newRegion);
            verify(signupValidator, never()).validateNicknameNotDuplicated(any(), any());
            verify(profileImageStorageClient, never()).deleteObject(any());
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile validates the new nickname against SignupValidator and succeeds when"
                    + " it's available")
    void updateMyProfile_succeeds_whenNicknameChangedAndAvailable() {
        User user = existingUser();
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("새닉네임", null);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(signupValidator.findActivityRegion(REGION_ID)).thenReturn(region);
            when(userRepository.saveAndFlush(user)).thenReturn(user);

            UserProfileResponse response = userProfileService.updateMyProfile(request);

            assertThat(response.nickname()).isEqualTo("새닉네임");
            verify(signupValidator).validateNicknameNotDuplicated("새닉네임", USER_ID);
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile throws DUPLICATE_NICKNAME when SignupValidator rejects the new"
                    + " nickname")
    void updateMyProfile_throwsDuplicateNickname_whenNicknameTakenByAnotherUser() {
        User user = existingUser();
        UserProfileUpdateRequest request = updateRequest("새닉네임", null);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            doThrow(new BusinessException(ErrorCode.DUPLICATE_NICKNAME))
                    .when(signupValidator)
                    .validateNicknameNotDuplicated("새닉네임", USER_ID);

            assertThatThrownBy(() -> userProfileService.updateMyProfile(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);

            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile replaces the image key and deletes the old S3 object only after the"
                    + " save succeeds")
    void updateMyProfile_replacesImageKey_andDeletesOldObjectAfterSave() {
        User user = existingUser();
        ReflectionTestUtils.setField(user, "profileImageKey", "profiles/1/old.jpg");
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("길동", "profiles/1/new.jpg");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(signupValidator.findActivityRegion(REGION_ID)).thenReturn(region);
            when(userRepository.saveAndFlush(user)).thenReturn(user);

            userProfileService.updateMyProfile(request);

            assertThat(user.getProfileImageKey()).isEqualTo("profiles/1/new.jpg");
            verify(profileImageStorageClient, times(1)).deleteObject("profiles/1/old.jpg");
        }
    }

    @Test
    @DisplayName("updateMyProfile does not call deleteObject when this is the user's first upload")
    void updateMyProfile_doesNotDeleteObject_whenFirstUpload() {
        User user = existingUser();
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("길동", "profiles/1/first.jpg");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(signupValidator.findActivityRegion(REGION_ID)).thenReturn(region);
            when(userRepository.saveAndFlush(user)).thenReturn(user);

            userProfileService.updateMyProfile(request);

            assertThat(user.getProfileImageKey()).isEqualTo("profiles/1/first.jpg");
            verify(profileImageStorageClient, never()).deleteObject(any());
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile does not touch the image or call deleteObject when the same key is"
                    + " resent")
    void updateMyProfile_skipsImageUpdate_whenKeyUnchanged() {
        User user = existingUser();
        ReflectionTestUtils.setField(user, "profileImageKey", "profiles/1/old.jpg");
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("길동", "profiles/1/old.jpg");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(signupValidator.findActivityRegion(REGION_ID)).thenReturn(region);
            when(userRepository.saveAndFlush(user)).thenReturn(user);

            userProfileService.updateMyProfile(request);

            assertThat(user.getProfileImageKey()).isEqualTo("profiles/1/old.jpg");
            verify(profileImageStorageClient, never()).deleteObject(any());
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile throws INVALID_PROFILE_IMAGE_KEY when the key does not belong to the"
                    + " current user")
    void updateMyProfile_throwsInvalidProfileImageKey_whenKeyBelongsToAnotherUser() {
        User user = existingUser();
        UserProfileUpdateRequest request = updateRequest("길동", "profiles/999/other-user.jpg");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userProfileService.updateMyProfile(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PROFILE_IMAGE_KEY);

            verify(userRepository, never()).saveAndFlush(any());
            verify(profileImageStorageClient, never()).deleteObject(any());
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile does not delete the old image when a concurrent save conflict rolls"
                    + " back the update")
    void updateMyProfile_doesNotDeleteOldImage_whenSaveFails() {
        User user = existingUser();
        ReflectionTestUtils.setField(user, "profileImageKey", "profiles/1/old.jpg");
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("새닉네임", "profiles/1/new.jpg");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(signupValidator.findActivityRegion(REGION_ID)).thenReturn(region);
            when(userRepository.saveAndFlush(user))
                    .thenThrow(new DataIntegrityViolationException("duplicate entry"));
            when(signupValidator.resolveDuplicateException(any(), any(), any(), any()))
                    .thenReturn(new BusinessException(ErrorCode.DUPLICATE_NICKNAME));

            assertThatThrownBy(() -> userProfileService.updateMyProfile(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);

            verify(profileImageStorageClient, never()).deleteObject(any());
        }
    }

    @Test
    @DisplayName("updateMyProfile still returns successfully when deleting the old image throws")
    void updateMyProfile_doesNotFailUpdate_whenOldImageDeleteThrows() {
        User user = existingUser();
        ReflectionTestUtils.setField(user, "profileImageKey", "profiles/1/old.jpg");
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("길동", "profiles/1/new.jpg");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(signupValidator.findActivityRegion(REGION_ID)).thenReturn(region);
            when(userRepository.saveAndFlush(user)).thenReturn(user);
            doThrow(new RuntimeException("S3 unavailable"))
                    .when(profileImageStorageClient)
                    .deleteObject("profiles/1/old.jpg");

            UserProfileResponse response = userProfileService.updateMyProfile(request);

            assertThat(response.nickname()).isEqualTo("길동");
            assertThat(user.getProfileImageKey()).isEqualTo("profiles/1/new.jpg");
        }
    }

    @Test
    @DisplayName("createProfileImageUploadUrl builds an object key scoped to the current user")
    void createProfileImageUploadUrl_buildsScopedObjectKey() {
        ProfileImageUploadUrlRequest request =
                new ProfileImageUploadUrlRequest("jpg", "image/jpeg");
        ProfileImageStorageClient.ProfileImageUploadUrl mockUrl =
                new ProfileImageStorageClient.ProfileImageUploadUrl(
                        "https://mock-upload-url", 300L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(profileImageStorageClient.createUploadUrl(any(), any())).thenReturn(mockUrl);

            ProfileImageUploadUrlResponse response =
                    userProfileService.createProfileImageUploadUrl(request);

            assertThat(response.objectKey()).startsWith("profiles/" + USER_ID + "/");
            assertThat(response.objectKey()).endsWith(".jpg");
            assertThat(response.uploadUrl()).isEqualTo("https://mock-upload-url");
            assertThat(response.expiresInSeconds()).isEqualTo(300L);
        }
    }

    private User existingUser() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        User user =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        "기존 소개글",
                        true,
                        true,
                        false,
                        activityRegion,
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private UserProfileUpdateRequest updateRequest(String nickname, String profileImageKey) {
        return new UserProfileUpdateRequest(
                "홍길동",
                nickname,
                "새 소개글",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                REGION_ID,
                List.of(PostingCategory.WELFARE),
                profileImageKey);
    }
}
