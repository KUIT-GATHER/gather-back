package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialSignupSessionService {

    private static final int TOKEN_ISSUE_MAX_ATTEMPTS = 3;

    private final SocialSignupSessionRepository sessionRepository;
    private final SocialSignupSessionPersistenceService persistenceService;
    private final SocialSignupSessionConstraintResolver constraintResolver;
    private final SocialSignupTokenService tokenService;
    private final KakaoProperties kakaoProperties;
    private final Clock clock;

    public String issue(
            SocialProvider provider,
            RejoinBlockIdentifier identifier,
            EncryptedProviderUserId encryptedProviderUserId) {
        for (int attempt = 1; attempt <= TOKEN_ISSUE_MAX_ATTEMPTS; attempt++) {
            String token = tokenService.generateToken();
            String tokenHash = tokenService.hashToken(token);
            LocalDateTime now = LocalDateTime.now(clock);

            try {
                persistenceService.saveNew(
                        SocialSignupSession.create(
                                tokenHash,
                                provider,
                                identifier.hash(),
                                identifier.keyVersion(),
                                encryptedProviderUserId,
                                now.plusSeconds(kakaoProperties.signupTokenExpirationSeconds()),
                                now));
                return token;
            } catch (DataIntegrityViolationException exception) {
                if (!constraintResolver.isTokenHashConflict(exception)
                        || attempt == TOKEN_ISSUE_MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("가입 세션 token 발급 재시도 횟수를 초과했습니다.");
    }

    @Transactional(readOnly = true)
    public Optional<SocialSignupSessionIdentity> findIdentity(String token) {
        String tokenHash = tokenService.hashToken(token);
        return sessionRepository.findByTokenHash(tokenHash).map(this::toIdentity);
    }

    @Transactional(readOnly = true)
    public void validatePending(String token) {
        String tokenHash = tokenService.hashToken(token);
        SocialSignupSession session =
                sessionRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID));
        if (session.getStatus() != SocialSignupSessionStatus.PENDING) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        if (session.isExpiredAt(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_EXPIRED);
        }
    }

    private SocialSignupSessionIdentity toIdentity(SocialSignupSession session) {
        return new SocialSignupSessionIdentity(
                session.getProvider(),
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO,
                        session.getProviderUserKey(),
                        session.getProviderUserKeyVersion()),
                session.encryptedProviderUserId());
    }
}
