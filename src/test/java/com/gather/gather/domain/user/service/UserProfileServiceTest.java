package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
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

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userRepository, signupValidator);
    }

    @Test
    @DisplayName("getMyProfile returns the current user's profile")
    void getMyProfile_returnsProfile() {
        User user = existingUser();

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserProfileResponse response = userProfileService.getMyProfile();

            assertThat(response.nickname()).isEqualTo("길동");
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
        UserProfileUpdateRequest request = updateRequest("길동");

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
        }
    }

    @Test
    @DisplayName(
            "updateMyProfile validates the new nickname against SignupValidator and succeeds when"
                    + " it's available")
    void updateMyProfile_succeeds_whenNicknameChangedAndAvailable() {
        User user = existingUser();
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("새닉네임");

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
        UserProfileUpdateRequest request = updateRequest("새닉네임");

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
            "updateMyProfile throws DUPLICATE_NICKNAME when a concurrent save conflict is detected"
                    + " at flush time")
    void updateMyProfile_throwsDuplicateNickname_whenSaveFailsWithConflict() {
        User user = existingUser();
        Region region = Region.create("강남구", 2, "11680", null);
        UserProfileUpdateRequest request = updateRequest("새닉네임");

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

    private UserProfileUpdateRequest updateRequest(String nickname) {
        return new UserProfileUpdateRequest(
                "홍길동",
                nickname,
                "새 소개글",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                REGION_ID,
                List.of(PostingCategory.WELFARE));
    }
}
