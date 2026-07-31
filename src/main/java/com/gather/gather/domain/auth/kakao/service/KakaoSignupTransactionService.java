package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.AccountIdentityGuardService;
import com.gather.gather.domain.auth.service.AccountRejoinBlockService;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.SocialAccountConstraint;
import com.gather.gather.domain.auth.service.SocialAccountConstraintResolver;
import com.gather.gather.domain.auth.service.SocialAccountIdentityService;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoSignupTransactionService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialSignupSessionService signupSessionService;
    private final SocialAccountIdentityService socialAccountIdentityService;
    private final SignupValidator signupValidator;
    private final TokenIssuer tokenIssuer;
    private final SocialAccountProviderIdCipher providerIdCipher;
    private final SocialAccountConstraintResolver constraintResolver;
    private final AccountRejoinBlockService accountRejoinBlockService;
    private final AccountIdentityGuardService accountIdentityGuardService;
    private final Clock clock;

    /**
     * 가입 세션·User·SocialAccount·Refresh Token을 한 트랜잭션으로 저장한다. SocialAccount 식별자 충돌은 트랜잭션을 완전히 롤백한 뒤
     * 호출자가 재조회할 수 있도록 전용 예외로 전달한다.
     */
    @Transactional
    public TokenIssueResult createAccount(
            User user, String signupToken, String phoneNumber, String nickname) {
        LocalDateTime now = LocalDateTime.now(clock);
        LockedSocialSignupSession lockedSession =
                signupSessionService.lockForSignup(signupToken, now);
        SocialSignupIdentitySnapshot identity = lockedSession.identity();
        RejoinBlockIdentifier phoneIdentifier =
                accountIdentityGuardService.lockPhone(phoneNumber, now);
        validateRejoinAllowed(phoneIdentifier, identity, now);
        socialAccountIdentityService
                .findByProviderAndKey(identity.provider(), identity.identifier())
                .ifPresent(this::rejectExistingSocialAccount);

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(exception, null, phoneNumber, nickname);
        }

        EncryptedProviderUserId encryptedProviderUserId = identity.encryptedProviderUserId();
        String legacyProviderUserId = providerIdCipher.decrypt(encryptedProviderUserId);
        try {
            socialAccountRepository.saveAndFlush(
                    SocialAccount.createLinked(
                            savedUser,
                            identity.provider(),
                            legacyProviderUserId,
                            identity.identifier().hash(),
                            identity.identifier().keyVersion(),
                            encryptedProviderUserId,
                            now));
        } catch (DataIntegrityViolationException exception) {
            SocialAccountConstraint constraint = constraintResolver.resolve(exception);
            // staged dual-write 중에는 신규 HMAC과 legacy 평문 UNIQUE 모두 동일 카카오 계정 경쟁을 뜻한다.
            if (constraint == SocialAccountConstraint.PROVIDER_USER_KEY
                    || constraint == SocialAccountConstraint.LEGACY_PROVIDER_USER_ID) {
                log.warn("카카오 소셜 계정 UNIQUE 경쟁을 감지해 rollback 후 최신 상태를 재조회합니다.");
                throw new KakaoSignupIdentityConflictException(identity, exception);
            }
            throw exception;
        }

        TokenIssueResult tokens = tokenIssuer.issue(savedUser);
        lockedSession.consumeAndCancelOthers(now);
        return tokens;
    }

    private void rejectExistingSocialAccount(SocialAccount socialAccount) {
        if (socialAccount.isLinked()) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
    }

    private void validateRejoinAllowed(
            RejoinBlockIdentifier phoneIdentifier,
            SocialSignupIdentitySnapshot identity,
            LocalDateTime now) {
        if (accountRejoinBlockService.isBlockedForUpdate(phoneIdentifier, now)
                || accountRejoinBlockService.isBlocked(identity.identifier(), now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_REJOIN_BLOCKED);
        }
    }
}
