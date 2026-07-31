package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRejoinBlockServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 14, 30);
    private static final Long SOURCE_USER_ID = 10L;
    private static final RejoinBlockIdentifier PHONE_IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.PHONE, "a".repeat(64), 3);
    private static final RejoinBlockIdentifier KAKAO_IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, "b".repeat(64), 3);

    @Mock private AccountRejoinBlockRepository blockRepository;
    @Mock private RejoinBlockIdentifierHasher identifierHasher;

    private AccountRejoinBlockService service;

    @BeforeEach
    void setUp() {
        service = new AccountRejoinBlockService(blockRepository, identifierHasher);
    }

    @Test
    void isPhoneBlocked_hashesRawPhoneAndChecksExactExpirationBoundary() {
        when(identifierHasher.hashPhone("010-1234-5678")).thenReturn(PHONE_IDENTIFIER);
        when(blockRepository.existsByIdentifierTypeAndIdentifierHashAndExpiresAtAfter(
                        PHONE_IDENTIFIER.type(), PHONE_IDENTIFIER.hash(), NOW))
                .thenReturn(true);

        boolean blocked = service.isPhoneBlocked("010-1234-5678", NOW);

        assertThat(blocked).isTrue();
    }

    @Test
    void isKakaoBlocked_hashesRawProviderIdAndChecksExactExpirationBoundary() {
        when(identifierHasher.hashKakao("123456789")).thenReturn(KAKAO_IDENTIFIER);

        boolean blocked = service.isKakaoBlocked("123456789", NOW);

        assertThat(blocked).isFalse();
        verify(blockRepository)
                .existsByIdentifierTypeAndIdentifierHashAndExpiresAtAfter(
                        KAKAO_IDENTIFIER.type(), KAKAO_IDENTIFIER.hash(), NOW);
    }

    @Test
    void createOrExtendPhoneBlock_usesCapturedNowAndSevenDayExpiration() {
        when(identifierHasher.hashPhone("01012345678")).thenReturn(PHONE_IDENTIFIER);

        service.createOrExtendPhoneBlock("01012345678", SOURCE_USER_ID, NOW);

        verify(blockRepository)
                .upsertExtendingExpiration(
                        "PHONE",
                        PHONE_IDENTIFIER.hash(),
                        PHONE_IDENTIFIER.keyVersion(),
                        NOW.plusDays(7),
                        SOURCE_USER_ID,
                        NOW);
    }

    @Test
    void createOrExtendBlock_usesExistingHashedIdentifierWithoutHashingRawValue() {
        service.createOrExtendBlock(KAKAO_IDENTIFIER, SOURCE_USER_ID, NOW);

        verify(blockRepository)
                .upsertExtendingExpiration(
                        "KAKAO",
                        KAKAO_IDENTIFIER.hash(),
                        KAKAO_IDENTIFIER.keyVersion(),
                        NOW.plusDays(7),
                        SOURCE_USER_ID,
                        NOW);
    }

    @Test
    void createOrExtendPhoneAndKakaoBlocks_writesPhoneBeforeKakao() {
        when(identifierHasher.hashPhone("01012345678")).thenReturn(PHONE_IDENTIFIER);
        when(identifierHasher.hashKakao("123456789")).thenReturn(KAKAO_IDENTIFIER);

        service.createOrExtendPhoneAndKakaoBlocks("01012345678", "123456789", SOURCE_USER_ID, NOW);

        InOrder order = inOrder(blockRepository);
        order.verify(blockRepository)
                .upsertExtendingExpiration(
                        "PHONE",
                        PHONE_IDENTIFIER.hash(),
                        PHONE_IDENTIFIER.keyVersion(),
                        NOW.plusDays(7),
                        SOURCE_USER_ID,
                        NOW);
        order.verify(blockRepository)
                .upsertExtendingExpiration(
                        "KAKAO",
                        KAKAO_IDENTIFIER.hash(),
                        KAKAO_IDENTIFIER.keyVersion(),
                        NOW.plusDays(7),
                        SOURCE_USER_ID,
                        NOW);
    }

    @Test
    void createOrExtendPhoneBlock_rejectsMissingOperationTime() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> service.createOrExtendPhoneBlock("01012345678", SOURCE_USER_ID, null))
                .withMessage("재가입 제한 기준 시각은 필수입니다.");
    }
}
