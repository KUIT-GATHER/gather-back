package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SocialSignupSessionIssuanceIntegrationTest {

    private static final String COLLIDING_TOKEN = "A".repeat(43);
    private static final String RETRIED_TOKEN = "B".repeat(43);
    private static final String COLLIDING_HASH = "a".repeat(64);
    private static final String RETRIED_HASH = "b".repeat(64);

    @Autowired private SocialSignupSessionService sessionService;
    @Autowired private SocialSignupSessionRepository sessionRepository;
    @MockitoBean private SocialSignupTokenService tokenService;

    @AfterEach
    void tearDown() {
        Stream.of(COLLIDING_HASH, RETRIED_HASH)
                .map(sessionRepository::findByTokenHash)
                .flatMap(java.util.Optional::stream)
                .forEach(sessionRepository::delete);
        sessionRepository.flush();
    }

    @Test
    @DisplayName("실제 token hash UNIQUE 충돌은 독립 rollback 후 새 token으로 재시도한다")
    void issue_retriesWithNewTokenAfterActualMySqlUniqueConflict() {
        LocalDateTime now = LocalDateTime.now();
        RejoinBlockIdentifier identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO, "9".repeat(64), 3);
        EncryptedProviderUserId encryptedProviderUserId =
                new EncryptedProviderUserId("ciphertext", 4);
        sessionRepository.saveAndFlush(
                SocialSignupSession.createKakao(
                        COLLIDING_HASH,
                        identifier,
                        encryptedProviderUserId,
                        now.plusMinutes(15),
                        now));
        when(tokenService.generateToken()).thenReturn(COLLIDING_TOKEN, RETRIED_TOKEN);
        when(tokenService.validateAndHash(COLLIDING_TOKEN)).thenReturn(COLLIDING_HASH);
        when(tokenService.validateAndHash(RETRIED_TOKEN)).thenReturn(RETRIED_HASH);

        String issued = sessionService.issue(identifier, encryptedProviderUserId);

        assertThat(issued).isEqualTo(RETRIED_TOKEN);
        assertThat(sessionRepository.findByTokenHash(COLLIDING_HASH)).isPresent();
        assertThat(sessionRepository.findByTokenHash(RETRIED_HASH)).isPresent();
        verify(tokenService, times(2)).generateToken();
    }
}
