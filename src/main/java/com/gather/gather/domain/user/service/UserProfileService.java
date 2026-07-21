package com.gather.gather.domain.user.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.dto.UserProfileUpdateRequest;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final SignupValidator signupValidator;

    public UserProfileResponse getMyProfile() {
        return UserProfileResponse.of(findCurrentUser());
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

        user.updateProfile(
                request.name(),
                request.nickname(),
                introduction,
                request.birthDate(),
                request.gender(),
                activityRegion,
                request.interestCategories());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(
                    exception, user.getEmail(), user.getPhoneNumber(), request.nickname());
        }

        return UserProfileResponse.of(user);
    }

    private User findCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
