package com.gather.gather.domain.user.service;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.AccountLoginTypeResolver;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.dto.UserProfileUpdateRequest;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
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

    private final UserRepository userRepository;
    private final SignupValidator signupValidator;
    private final AccountLoginTypeResolver accountLoginTypeResolver;

    public UserProfileResponse getMyProfile() {
        User user = findCurrentUser();
        return UserProfileResponse.of(user, resolveLoginType(user));
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

        return UserProfileResponse.of(user, resolveLoginType(user));
    }

    private User findCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 프로필 응답의 loginType을 판정한다.
     *
     * <p>정상 계정이라면 EMAIL 또는 KAKAO 중 하나여야 하므로, 판정 불가는 credential 데이터가 깨진 상태다. 프론트가 비밀번호 변경 UI를 잘못
     * 노출하지 않도록 null을 조용히 내려보내지 않고 실패시킨다.
     */
    private AccountLoginType resolveLoginType(User user) {
        return accountLoginTypeResolver
                .resolveCredentialType(user)
                .orElseThrow(
                        () -> {
                            log.error(
                                    "프로필 조회 중 로그인 credential 불일치를 감지했습니다: userId={}", user.getId());
                            return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                        });
    }
}
