package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenProvider;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.LoginPolicy;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.auth.service.WithdrawalPolicy;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoLoginResolver {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final SocialSignupTokenProvider socialSignupTokenProvider;
    private final TokenIssuer tokenIssuer;
    private final LoginPolicy loginPolicy;
    private final WithdrawalPolicy withdrawalPolicy;

    @Transactional
    public KakaoLoginResult resolve(
            SocialProvider provider, String providerUserId, String nickname) {
        return socialAccountRepository
                .findByProviderAndProviderUserIdForUpdate(provider, providerUserId)
                .map(account -> resolveLinkedAccount(account, provider, providerUserId, nickname))
                .orElseGet(() -> additionalInfoRequired(provider, providerUserId, nickname));
    }

    private KakaoLoginResult resolveLinkedAccount(
            SocialAccount account,
            SocialProvider provider,
            String providerUserId,
            String nickname) {
        User user =
                userRepository
                        .findByIdForUpdate(account.getUser().getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN
                && user.getWithdrawnAt() != null
                && withdrawalPolicy.isGracePeriodOver(user.getWithdrawnAt(), LocalDateTime.now())) {
            socialAccountRepository.delete(account);
            return additionalInfoRequired(provider, providerUserId, nickname);
        }

        loginPolicy.validateLoginAllowed(user);
        return KakaoLoginResult.loginCompleted(tokenIssuer.issue(user));
    }

    private KakaoLoginResult additionalInfoRequired(
            SocialProvider provider, String providerUserId, String nickname) {
        return KakaoLoginResult.additionalInfoRequired(
                socialSignupTokenProvider.createSignupToken(provider, providerUserId), nickname);
    }
}
