package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User 잠금 아래에서 최신 계정 상태를 재검증하고 토큰을 발급한다.
 *
 * <p>카카오 외부 HTTP 호출이 끝난 뒤 이 짧은 트랜잭션에 진입해, 탈퇴가 시작된 계정에 새 Refresh Token이 남는 경쟁을 막는다.
 */
@Service
@RequiredArgsConstructor
public class LockedTokenIssuanceService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final TokenIssuer tokenIssuer;
    private final LoginPolicy loginPolicy;

    @Transactional
    public TokenIssueResult issue(Long userId) {
        User user = findUserForUpdate(userId);
        loginPolicy.validateLoginAllowed(user);
        return tokenIssuer.issue(user);
    }

    @Transactional
    public TokenIssueResult issueForSocialAccount(Long userId, Long socialAccountId) {
        User user = findUserForUpdate(userId);
        loginPolicy.validateLoginAllowed(user);
        SocialAccount socialAccount =
                socialAccountRepository
                        .findByIdForUpdate(socialAccountId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED));
        if (!socialAccount.getUser().getId().equals(user.getId()) || !socialAccount.isLinked()) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
        }
        return tokenIssuer.issue(user);
    }

    private User findUserForUpdate(Long userId) {
        return userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));
    }
}
