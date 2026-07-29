package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoSignupTransactionService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialSignupSessionRepository signupSessionRepository;
    private final SocialSignupTokenService signupTokenService;
    private final SocialAccountIdentityService socialAccountIdentityService;
    private final SignupValidator signupValidator;
    private final TokenIssuer tokenIssuer;
    private final SocialAccountProviderIdCipher providerIdCipher;
    private final SocialAccountConstraintResolver constraintResolver;
    private final Clock clock;

    /**
     * 가입 세션·User·SocialAccount·Refresh Token을 한 트랜잭션으로 저장한다. SocialAccount 식별자 충돌은 트랜잭션을 완전히
     * 롤백한 뒤 호출자가 재조회할 수 있도록 전용 예외로 전달한다.
     */
    @Transactional
    public TokenIssueResult createAccount(
            User user, String signupToken, String phoneNumber, String nickname) {
        String tokenHash = signupTokenService.hashToken(signupToken);
        SocialSignupSession candidate =
                signupSessionRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID));
        if (candidate.getStatus() != SocialSignupSessionStatus.PENDING) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }

        List<SocialSignupSession> lockedSessions =
                signupSessionRepository.findAllByIdentityAndStatusForUpdate(
                        candidate.getProvider(),
                        candidate.getProviderUserKey(),
                        SocialSignupSessionStatus.PENDING);
        SocialSignupSession session =
                lockedSessions.stream()
                        .filter(locked -> locked.getTokenHash().equals(tokenHash))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID));
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_EXPIRED);
        }

        RejoinBlockIdentifier identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO,
                        session.getProviderUserKey(),
                        session.getProviderUserKeyVersion());
        socialAccountIdentityService
                .findByProviderAndKey(session.getProvider(), identifier)
                .ifPresent(this::rejectExistingSocialAccount);

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(exception, null, phoneNumber, nickname);
        }

        EncryptedProviderUserId encryptedProviderUserId = session.encryptedProviderUserId();
        String legacyProviderUserId = providerIdCipher.decrypt(encryptedProviderUserId);
        try {
            socialAccountRepository.saveAndFlush(
                    SocialAccount.createLinked(
                            savedUser,
                            session.getProvider(),
                            legacyProviderUserId,
                            identifier.hash(),
                            identifier.keyVersion(),
                            encryptedProviderUserId,
                            now));
        } catch (DataIntegrityViolationException exception) {
            SocialAccountConstraint constraint = constraintResolver.resolve(exception);
            // staged dual-write 중에는 신규 HMAC과 legacy 평문 UNIQUE 모두 동일 카카오 계정 경쟁을 뜻한다.
            if (constraint == SocialAccountConstraint.PROVIDER_USER_KEY
                    || constraint == SocialAccountConstraint.LEGACY_PROVIDER_USER_ID) {
                throw new SocialAccountProviderKeyConflictException(exception);
            }
            throw exception;
        }

        TokenIssueResult tokens = tokenIssuer.issue(savedUser);
        session.consume(now);
        lockedSessions.stream()
                .filter(locked -> !locked.getTokenHash().equals(tokenHash))
                .forEach(locked -> locked.cancel(now));
        return tokens;
    }

    private void rejectExistingSocialAccount(SocialAccount socialAccount) {
        if (socialAccount.isLinked()) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
    }
}
