package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialSignupSessionService {

    private static final int TOKEN_ISSUE_MAX_ATTEMPTS = 3;

    private final SocialSignupSessionRepository sessionRepository;
    private final SocialSignupSessionPersistenceService persistenceService;
    private final SocialSignupSessionConstraintResolver constraintResolver;
    private final SocialSignupTokenService tokenService;
    private final KakaoProperties kakaoProperties;
    private final Clock clock;

    public String issue(
            RejoinBlockIdentifier identifier, EncryptedProviderUserId encryptedProviderUserId) {
        DataIntegrityViolationException lastConflict = null;
        for (int attempt = 1; attempt <= TOKEN_ISSUE_MAX_ATTEMPTS; attempt++) {
            String token = tokenService.generateToken();
            String tokenHash = tokenService.validateAndHash(token);
            LocalDateTime now = LocalDateTime.now(clock);

            try {
                persistenceService.saveNewAttempt(
                        SocialSignupSession.createKakao(
                                tokenHash,
                                identifier,
                                encryptedProviderUserId,
                                now.plusSeconds(kakaoProperties.signupTokenExpirationSeconds()),
                                now));
                return token;
            } catch (DataIntegrityViolationException exception) {
                if (!constraintResolver.isTokenHashConflict(exception)) {
                    throw exception;
                }
                lastConflict = exception;
                if (attempt < TOKEN_ISSUE_MAX_ATTEMPTS) {
                    log.warn(
                            "가입 세션 token hash 충돌로 발급을 재시도합니다. attempt={}, maxAttempts={}",
                            attempt,
                            TOKEN_ISSUE_MAX_ATTEMPTS);
                }
            }
        }
        log.error("가입 세션 token hash 충돌 재시도 횟수를 소진했습니다. maxAttempts={}", TOKEN_ISSUE_MAX_ATTEMPTS);
        throw new IllegalStateException("가입 세션 token 발급 재시도 횟수를 초과했습니다.", lastConflict);
    }

    @Transactional(readOnly = true)
    public void validateUsable(String rawToken) {
        String tokenHash = tokenService.validateAndHash(rawToken);
        SocialSignupSession session = findByTokenHash(tokenHash);
        requireUsable(session, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedSocialSignupSession lockForSignup(String rawToken, LocalDateTime now) {
        String tokenHash = tokenService.validateAndHash(rawToken);
        SocialSignupSession candidate = findByTokenHash(tokenHash);
        requireUsable(candidate, now);

        List<SocialSignupSession> lockedPendingSessions =
                sessionRepository.findAllByIdentityAndStatusForUpdate(
                        candidate.getProvider(),
                        candidate.getProviderUserKey(),
                        SocialSignupSessionStatus.PENDING);
        SocialSignupSession target =
                lockedPendingSessions.stream()
                        .filter(session -> session.getTokenHash().equals(tokenHash))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID));
        requireUsable(target, now);
        return new LockedSocialSignupSession(
                target, lockedPendingSessions, toIdentitySnapshot(target));
    }

    private SocialSignupSession findByTokenHash(String tokenHash) {
        return sessionRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID));
    }

    private void requireUsable(SocialSignupSession session, LocalDateTime now) {
        if (session.getStatus() != SocialSignupSessionStatus.PENDING) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        if (session.isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_EXPIRED);
        }
    }

    private SocialSignupIdentitySnapshot toIdentitySnapshot(SocialSignupSession session) {
        return new SocialSignupIdentitySnapshot(
                session.getProvider(),
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO,
                        session.getProviderUserKey(),
                        session.getProviderUserKeyVersion()),
                session.encryptedProviderUserId());
    }
}
