package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SocialSignupSessionServiceTest {

    private static final String TOKEN = "A".repeat(43);
    private static final String TOKEN_HASH = "b".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 12, 0);
    private static final RejoinBlockIdentifier IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, "c".repeat(64), 3);
    private static final EncryptedProviderUserId ENCRYPTED_PROVIDER_USER_ID =
            new EncryptedProviderUserId("ciphertext", 4);

    @Mock private SocialSignupSessionRepository repository;
    @Mock private SocialSignupSessionPersistenceService persistenceService;
    @Mock private SocialSignupTokenService tokenService;

    private SocialSignupSessionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service =
                new SocialSignupSessionService(
                        repository,
                        persistenceService,
                        new SocialSignupSessionConstraintResolver(),
                        tokenService,
                        new KakaoProperties(
                                "rest-api-key",
                                "client-secret",
                                List.of("https://gathernow.kr/login/kakao/callback"),
                                900,
                                "https://kauth.kakao.com",
                                "https://kapi.kakao.com"),
                        clock);
    }

    @Test
    @DisplayName("세션 발급은 token 원문 대신 SHA-256 hash와 identity snapshot만 저장한다")
    void issue_persistsHashWithoutRawToken() {
        when(tokenService.generateToken()).thenReturn(TOKEN);
        when(tokenService.validateAndHash(TOKEN)).thenReturn(TOKEN_HASH);
        doNothing().when(persistenceService).saveNewAttempt(any(SocialSignupSession.class));

        String issued = service.issue(IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

        ArgumentCaptor<SocialSignupSession> captor =
                ArgumentCaptor.forClass(SocialSignupSession.class);
        verify(persistenceService).saveNewAttempt(captor.capture());
        SocialSignupSession session = captor.getValue();
        assertThat(issued).isEqualTo(TOKEN);
        assertThat(session.getTokenHash()).isEqualTo(TOKEN_HASH).isNotEqualTo(TOKEN);
        assertThat(session.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(session.getProviderUserKey()).isEqualTo(IDENTIFIER.hash());
        assertThat(session.getProviderUserKeyVersion()).isEqualTo(3);
        assertThat(session.getProviderUserIdCiphertext()).isEqualTo("ciphertext");
        assertThat(session.getEncryptionKeyVersion()).isEqualTo(4);
        assertThat(
                        java.time.Duration.between(session.getCreatedAt(), session.getExpiresAt())
                                .toSeconds())
                .isEqualTo(900);
    }

    @Test
    @DisplayName("token hash UNIQUE 충돌이면 새 token으로 최대 횟수 안에서 재시도한다")
    void issue_retriesWithNewTokenOnlyForTokenHashConflict() {
        String secondToken = "B".repeat(43);
        String secondHash = "d".repeat(64);
        when(tokenService.generateToken()).thenReturn(TOKEN, secondToken);
        when(tokenService.validateAndHash(TOKEN)).thenReturn(TOKEN_HASH);
        when(tokenService.validateAndHash(secondToken)).thenReturn(secondHash);
        doThrow(tokenHashConflict())
                .doNothing()
                .when(persistenceService)
                .saveNewAttempt(any(SocialSignupSession.class));

        String issued = service.issue(IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

        ArgumentCaptor<SocialSignupSession> captor =
                ArgumentCaptor.forClass(SocialSignupSession.class);
        verify(persistenceService, times(2)).saveNewAttempt(captor.capture());
        assertThat(issued).isEqualTo(secondToken);
        assertThat(captor.getAllValues())
                .extracting(SocialSignupSession::getTokenHash)
                .containsExactly(TOKEN_HASH, secondHash);
    }

    @Test
    @DisplayName("token hash 외 무결성 위반은 재시도하지 않고 원본을 유지한다")
    void issue_doesNotRetryUnrelatedIntegrityViolation() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("other constraint");
        when(tokenService.generateToken()).thenReturn(TOKEN);
        when(tokenService.validateAndHash(TOKEN)).thenReturn(TOKEN_HASH);
        doThrow(exception).when(persistenceService).saveNewAttempt(any(SocialSignupSession.class));

        assertThatThrownBy(() -> service.issue(IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID))
                .isSameAs(exception);
        verify(persistenceService).saveNewAttempt(any(SocialSignupSession.class));
        verify(tokenService).generateToken();
    }

    @Test
    @DisplayName("token hash 충돌 재시도 소진 예외는 마지막 DB 예외를 원인으로 보존한다")
    void issue_exhaustedRetries_preservesLastConflict() {
        DataIntegrityViolationException first = tokenHashConflict();
        DataIntegrityViolationException second = tokenHashConflict();
        DataIntegrityViolationException last = tokenHashConflict();
        when(tokenService.generateToken()).thenReturn(TOKEN);
        when(tokenService.validateAndHash(TOKEN)).thenReturn(TOKEN_HASH);
        doThrow(first, second, last)
                .when(persistenceService)
                .saveNewAttempt(any(SocialSignupSession.class));

        assertThatThrownBy(() -> service.issue(IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(last);
        verify(persistenceService, times(3)).saveNewAttempt(any(SocialSignupSession.class));
        verify(tokenService, times(3)).generateToken();
    }

    @Test
    @DisplayName("정상 PENDING 세션은 사전 검증을 통과한다")
    void validateUsable_pendingSession_succeeds() {
        SocialSignupSession session = session(TOKEN_HASH, NOW.plusNanos(1));
        stubTokenAndCandidate(session);

        service.validateUsable(TOKEN);

        verify(repository).findByTokenHash(TOKEN_HASH);
    }

    @Test
    @DisplayName("세션이 없거나 terminal 상태면 SIGNUP_TOKEN_INVALID다")
    void validateUsable_missingOrTerminalSession_isInvalid() {
        when(tokenService.validateAndHash(TOKEN)).thenReturn(TOKEN_HASH);
        when(repository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());
        assertErrorCode(() -> service.validateUsable(TOKEN), ErrorCode.SIGNUP_TOKEN_INVALID);

        SocialSignupSession consumed = session(TOKEN_HASH, NOW.plusMinutes(1));
        consumed.consume(NOW.minusMinutes(1));
        when(repository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(consumed));
        assertErrorCode(() -> service.validateUsable(TOKEN), ErrorCode.SIGNUP_TOKEN_INVALID);

        SocialSignupSession cancelled = session(TOKEN_HASH, NOW.plusMinutes(1));
        cancelled.cancel(NOW.minusMinutes(1));
        when(repository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(cancelled));
        assertErrorCode(() -> service.validateUsable(TOKEN), ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("만료 시각 이전만 유효하고 동일하거나 지난 시각은 만료다")
    void validateUsable_usesExactExpirationBoundary() {
        when(tokenService.validateAndHash(TOKEN)).thenReturn(TOKEN_HASH);

        when(repository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(session(TOKEN_HASH, NOW.minusNanos(1))));
        assertErrorCode(() -> service.validateUsable(TOKEN), ErrorCode.SIGNUP_TOKEN_EXPIRED);

        when(repository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(session(TOKEN_HASH, NOW)));
        assertErrorCode(() -> service.validateUsable(TOKEN), ErrorCode.SIGNUP_TOKEN_EXPIRED);

        when(repository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(session(TOKEN_HASH, NOW.plusNanos(1))));
        service.validateUsable(TOKEN);
    }

    @Test
    @DisplayName("token 형식 오류는 repository 조회 전에 차단한다")
    void validateUsable_invalidFormat_doesNotQueryRepository() {
        BusinessException invalid = new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        when(tokenService.validateAndHash("invalid")).thenThrow(invalid);

        assertThatThrownBy(() -> service.validateUsable("invalid")).isSameAs(invalid);
        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("가입 잠금은 identity의 PENDING 목록에서 token hash로 대상을 선택한다")
    void lockForSignup_returnsTargetSiblingsAndIdentitySnapshot() {
        SocialSignupSession candidate = session(TOKEN_HASH, NOW.plusMinutes(1));
        SocialSignupSession sibling = session("d".repeat(64), NOW.plusMinutes(1));
        stubTokenAndCandidate(candidate);
        when(repository.findAllByIdentityAndStatusForUpdate(
                        SocialProvider.KAKAO, IDENTIFIER.hash(), SocialSignupSessionStatus.PENDING))
                .thenReturn(List.of(sibling, candidate));

        LockedSocialSignupSession locked = service.lockForSignup(TOKEN, NOW);

        assertThat(locked.target()).isSameAs(candidate);
        assertThat(locked.lockedPendingSessions()).containsExactly(sibling, candidate);
        assertThatThrownBy(() -> locked.lockedPendingSessions().add(candidate))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(locked.identity().provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(locked.identity().identifier()).isEqualTo(IDENTIFIER);
        assertThat(locked.identity().encryptedProviderUserId())
                .isEqualTo(ENCRYPTED_PROVIDER_USER_ID);
    }

    @Test
    @DisplayName("잠금 목록에서 대상이 사라지면 invalid로 차단한다")
    void lockForSignup_targetMissingAfterLock_isInvalid() {
        SocialSignupSession candidate = session(TOKEN_HASH, NOW.plusMinutes(1));
        stubTokenAndCandidate(candidate);
        when(repository.findAllByIdentityAndStatusForUpdate(any(), any(), any()))
                .thenReturn(List.of());

        assertErrorCode(() -> service.lockForSignup(TOKEN, NOW), ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("잠금 후 대상 상태와 만료를 다시 검증한다")
    void lockForSignup_revalidatesTargetAfterLock() {
        SocialSignupSession candidate = session(TOKEN_HASH, NOW.plusMinutes(1));
        SocialSignupSession consumed = session(TOKEN_HASH, NOW.plusMinutes(1));
        consumed.consume(NOW.minusMinutes(1));
        stubTokenAndCandidate(candidate);
        when(repository.findAllByIdentityAndStatusForUpdate(any(), any(), any()))
                .thenReturn(List.of(consumed));
        assertErrorCode(() -> service.lockForSignup(TOKEN, NOW), ErrorCode.SIGNUP_TOKEN_INVALID);

        SocialSignupSession expired = session(TOKEN_HASH, NOW);
        when(repository.findAllByIdentityAndStatusForUpdate(any(), any(), any()))
                .thenReturn(List.of(expired));
        assertErrorCode(() -> service.lockForSignup(TOKEN, NOW), ErrorCode.SIGNUP_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("잠금 context는 대상만 소비하고 다른 PENDING만 취소한다")
    void lockedContext_consumesTargetAndCancelsOnlyPendingSiblings() {
        SocialSignupSession target = session(TOKEN_HASH, NOW.plusMinutes(1));
        SocialSignupSession sibling = session("d".repeat(64), NOW.plusMinutes(1));
        SocialSignupSession terminal = session("e".repeat(64), NOW.plusMinutes(1));
        terminal.consume(NOW.minusMinutes(1));
        LockedSocialSignupSession locked =
                new LockedSocialSignupSession(
                        target,
                        List.of(target, sibling, terminal),
                        new SocialSignupIdentitySnapshot(
                                SocialProvider.KAKAO, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID));

        locked.consumeAndCancelOthers(NOW);

        assertThat(target.getStatus()).isEqualTo(SocialSignupSessionStatus.CONSUMED);
        assertThat(sibling.getStatus()).isEqualTo(SocialSignupSessionStatus.CANCELLED);
        assertThat(terminal.getStatus()).isEqualTo(SocialSignupSessionStatus.CONSUMED);
    }

    private void stubTokenAndCandidate(SocialSignupSession session) {
        when(tokenService.validateAndHash(TOKEN)).thenReturn(TOKEN_HASH);
        when(repository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(session));
    }

    private SocialSignupSession session(String tokenHash, LocalDateTime expiresAt) {
        return SocialSignupSession.createKakao(
                tokenHash, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID, expiresAt, NOW.minusMinutes(2));
    }

    private DataIntegrityViolationException tokenHashConflict() {
        return new DataIntegrityViolationException(
                "Duplicate entry for key 'uk_social_signup_session_token_hash'");
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
