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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
        if (!request.nickname().equals(user.getNickname())) {
            signupValidator.validateNicknameNotDuplicated(request.nickname(), user.getId());
        }
        signupValidator.validateActivityRegionId(request.activityRegionId());
        Region activityRegion = signupValidator.findActivityRegion(request.activityRegionId());
        signupValidator.validateInterestCategories(request.interestCategories());
        String introduction = signupValidator.normalizeNullableText(request.introduction());
        validateProfileImageKeyOwnership(user.getId(), request.profileImageKey());

        String oldProfileImageKey = user.getProfileImageKey();
        boolean profileImageKeyChanged =
                request.profileImageKey() != null
                        && !request.profileImageKey().equals(oldProfileImageKey);

        user.updateProfile(
                request.name(),
                request.nickname(),
                introduction,
                request.birthDate(),
                request.gender(),
                activityRegion,
                request.interestCategories());
        if (profileImageKeyChanged) {
            user.updateProfileImageKey(request.profileImageKey());
        }

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(
                    exception, user.getEmail(), user.getPhoneNumber(), request.nickname());
        }

        // DB 반영이 확정된 뒤에만 이전 사진을 정리한다 — 동시성 경합으로 위 saveAndFlush가 실패해 롤백되면 이 줄까지 도달하지 않으므로,
        // 실패한 수정 요청 때문에 여전히 참조 중인 사진이 지워지는 사고를 막는다.
        if (profileImageKeyChanged && oldProfileImageKey != null) {
            deleteOldProfileImageBestEffort(oldProfileImageKey);
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

    /**
     * 클라이언트가 보낸 profileImageKey가 실제로 본인이 업로드 URL 발급 API로 받은 키(profiles/{내 userId}/...)인지 확인한다. 이
     * 검사가 없으면 다른 사용자의 키를 그대로 넣어뒀다가 다음 수정 때 그 사용자의 실제 S3 오브젝트가 삭제되는 경로가 생긴다.
     */
    private void validateProfileImageKeyOwnership(Long userId, String profileImageKey) {
        if (profileImageKey == null) {
            return;
        }
        String expectedPrefix = PROFILE_IMAGE_KEY_PREFIX + userId + "/";
        if (!profileImageKey.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
    }

    /** S3 삭제는 프로필 수정 자체의 성패와 무관한 뒷정리다 — 실패해도 이미 커밋된 프로필 수정을 되돌리지 않고 경고만 남긴다. */
    private void deleteOldProfileImageBestEffort(String objectKey) {
        try {
            profileImageStorageClient.deleteObject(objectKey);
        } catch (RuntimeException exception) {
            log.warn("이전 프로필 사진 삭제에 실패했습니다. objectKey={}", objectKey, exception);
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
