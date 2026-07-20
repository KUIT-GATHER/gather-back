package com.gather.gather.domain.user.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.user.dto.ProfileImageUploadUrlRequest;
import com.gather.gather.domain.user.dto.ProfileImageUploadUrlResponse;
import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.dto.UserProfileUpdateRequest;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private static final String PROFILE_IMAGE_KEY_PREFIX = "profiles/";

    private final UserRepository userRepository;
    private final SignupValidator signupValidator;
    private final ProfileImageStorageClient profileImageStorageClient;

    public UserProfileResponse getMyProfile() {
        User user = findCurrentUser();
        return UserProfileResponse.of(user, buildProfileImageUrl(user));
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UserProfileUpdateRequest request) {
        User user = findCurrentUser();

        signupValidator.validateName(request.name());
        signupValidator.validateNickname(request.nickname());
        if (!request.nickname().equals(user.getNickname())
                && userRepository.existsByNicknameAndIdNot(request.nickname(), user.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        signupValidator.validateActivityRegionId(request.activityRegionId());
        Region activityRegion = signupValidator.findActivityRegion(request.activityRegionId());
        signupValidator.validateInterestCategories(request.interestCategories());
        String introduction = signupValidator.normalizeNullableText(request.introduction());

        user.updateProfile(
                request.name(),
                request.nickname(),
                introduction,
                request.birthDate(),
                request.gender(),
                activityRegion,
                request.interestCategories());
        replaceProfileImageKeyIfChanged(user, request.profileImageKey());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(
                    exception, user.getEmail(), user.getPhoneNumber(), request.nickname());
        }
        return UserProfileResponse.of(user, buildProfileImageUrl(user));
    }

    public ProfileImageUploadUrlResponse createProfileImageUploadUrl(
            ProfileImageUploadUrlRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        String objectKey =
                PROFILE_IMAGE_KEY_PREFIX
                        + userId
                        + "/"
                        + UUID.randomUUID()
                        + "."
                        + request.fileExtension().toLowerCase();

        ProfileImageStorageClient.ProfileImageUploadUrl uploadUrl =
                profileImageStorageClient.createUploadUrl(objectKey, request.contentType());
        return new ProfileImageUploadUrlResponse(
                uploadUrl.uploadUrl(), objectKey, uploadUrl.expiresInSeconds());
    }

    private void replaceProfileImageKeyIfChanged(User user, String newProfileImageKey) {
        if (newProfileImageKey == null || newProfileImageKey.equals(user.getProfileImageKey())) {
            return;
        }
        String oldProfileImageKey = user.getProfileImageKey();
        user.updateProfileImageKey(newProfileImageKey);
        if (oldProfileImageKey != null) {
            profileImageStorageClient.deleteObject(oldProfileImageKey);
        }
    }

    private String buildProfileImageUrl(User user) {
        return user.getProfileImageKey() == null
                ? null
                : profileImageStorageClient.buildPublicUrl(user.getProfileImageKey());
    }

    private User findCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
