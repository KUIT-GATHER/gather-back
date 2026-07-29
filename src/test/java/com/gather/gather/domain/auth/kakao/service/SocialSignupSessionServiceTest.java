package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

    @Mock private SocialSignupSessionRepository repository;
    @Mock private SocialSignupSessionPersistenceService persistenceService;
    @Mock private SocialSignupTokenService tokenService;

    private SocialSignupSessionService service;

    @BeforeEach
    void setUp() {
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
                        Clock.fixed(Instant.parse("2026-07-30T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("세션 발급은 token 원문 대신 SHA-256 hash와 identity snapshot만 저장한다")
    void issue_persistsHashWithoutRawToken() {
        RejoinBlockIdentifier identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO, "c".repeat(64), 3);
        EncryptedProviderUserId encryptedProviderUserId =
                new EncryptedProviderUserId("ciphertext", 4);
        when(tokenService.generateToken()).thenReturn(TOKEN);
        when(tokenService.hashToken(TOKEN)).thenReturn(TOKEN_HASH);
        doNothing().when(persistenceService).saveNew(any(SocialSignupSession.class));

        String issued = service.issue(SocialProvider.KAKAO, identifier, encryptedProviderUserId);

        ArgumentCaptor<SocialSignupSession> captor =
                ArgumentCaptor.forClass(SocialSignupSession.class);
        verify(persistenceService).saveNew(captor.capture());
        SocialSignupSession session = captor.getValue();
        assertThat(issued).isEqualTo(TOKEN);
        assertThat(session.getTokenHash()).isEqualTo(TOKEN_HASH).isNotEqualTo(TOKEN);
        assertThat(session.getProviderUserKey()).isEqualTo(identifier.hash());
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
        String secondHash = "c".repeat(64);
        RejoinBlockIdentifier identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO, "d".repeat(64), 3);
        EncryptedProviderUserId encryptedProviderUserId =
                new EncryptedProviderUserId("ciphertext", 4);
        when(tokenService.generateToken()).thenReturn(TOKEN, secondToken);
        when(tokenService.hashToken(TOKEN)).thenReturn(TOKEN_HASH);
        when(tokenService.hashToken(secondToken)).thenReturn(secondHash);
        doThrow(tokenHashConflict())
                .doNothing()
                .when(persistenceService)
                .saveNew(any(SocialSignupSession.class));

        String issued = service.issue(SocialProvider.KAKAO, identifier, encryptedProviderUserId);

        ArgumentCaptor<SocialSignupSession> captor =
                ArgumentCaptor.forClass(SocialSignupSession.class);
        verify(persistenceService, times(2)).saveNew(captor.capture());
        assertThat(issued).isEqualTo(secondToken);
        assertThat(captor.getAllValues())
                .extracting(SocialSignupSession::getTokenHash)
                .containsExactly(TOKEN_HASH, secondHash);
    }

    @Test
    @DisplayName("token hash 외 무결성 위반은 재시도하지 않는다")
    void issue_doesNotRetryUnrelatedIntegrityViolation() {
        RejoinBlockIdentifier identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO, "e".repeat(64), 3);
        EncryptedProviderUserId encryptedProviderUserId =
                new EncryptedProviderUserId("ciphertext", 4);
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("other constraint");
        when(tokenService.generateToken()).thenReturn(TOKEN);
        when(tokenService.hashToken(TOKEN)).thenReturn(TOKEN_HASH);
        doThrow(exception).when(persistenceService).saveNew(any(SocialSignupSession.class));

        assertThatThrownBy(
                        () ->
                                service.issue(
                                        SocialProvider.KAKAO, identifier, encryptedProviderUserId))
                .isSameAs(exception);
        verify(persistenceService).saveNew(any(SocialSignupSession.class));
        verify(tokenService).generateToken();
    }

    @Test
    @DisplayName("token hash UNIQUE 충돌은 최대 세 번까지만 시도한다")
    void issue_stopsAfterBoundedTokenHashRetries() {
        RejoinBlockIdentifier identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO, "f".repeat(64), 3);
        EncryptedProviderUserId encryptedProviderUserId =
                new EncryptedProviderUserId("ciphertext", 4);
        DataIntegrityViolationException exception = tokenHashConflict();
        when(tokenService.generateToken()).thenReturn(TOKEN);
        when(tokenService.hashToken(TOKEN)).thenReturn(TOKEN_HASH);
        doThrow(exception).when(persistenceService).saveNew(any(SocialSignupSession.class));

        assertThatThrownBy(
                        () ->
                                service.issue(
                                        SocialProvider.KAKAO, identifier, encryptedProviderUserId))
                .isSameAs(exception);
        verify(persistenceService, times(3)).saveNew(any(SocialSignupSession.class));
        verify(tokenService, times(3)).generateToken();
    }

    private DataIntegrityViolationException tokenHashConflict() {
        return new DataIntegrityViolationException(
                "Duplicate entry for key 'uk_social_signup_session_token_hash'");
    }
}
