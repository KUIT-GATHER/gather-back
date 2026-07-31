package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.service.AccountRejoinBlockService;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialSignupSessionPersistenceServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 5, 25, 56, 123_456_000);
    private static final RejoinBlockIdentifier IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, "a".repeat(64), 1);

    @Mock private SocialSignupSessionRepository sessionRepository;
    @Mock private AccountRejoinBlockService rejoinBlockService;

    @Test
    void saveNewAttemptChecksBlockAfterInsertHasJoinedIdentitySerialization() {
        SocialSignupSession session =
                SocialSignupSession.createKakao(
                        "b".repeat(64),
                        IDENTIFIER,
                        new EncryptedProviderUserId("ciphertext", 1),
                        NOW.plusMinutes(15),
                        NOW);
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);
        when(rejoinBlockService.isBlocked(IDENTIFIER, NOW)).thenReturn(true);
        SocialSignupSessionPersistenceService service =
                new SocialSignupSessionPersistenceService(sessionRepository, rejoinBlockService);

        assertThatThrownBy(() -> service.saveNewAttempt(session, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_REJOIN_BLOCKED);

        InOrder order = inOrder(sessionRepository, rejoinBlockService);
        order.verify(sessionRepository).saveAndFlush(session);
        order.verify(rejoinBlockService).isBlocked(IDENTIFIER, NOW);
    }
}
