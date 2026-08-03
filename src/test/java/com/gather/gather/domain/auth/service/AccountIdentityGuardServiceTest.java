package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountIdentityGuard;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.repository.AccountIdentityGuardRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountIdentityGuardServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 14, 30);
    private static final RejoinBlockIdentifier PHONE_IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.PHONE, "a".repeat(64), 3);

    @Mock private AccountIdentityGuardRepository guardRepository;
    @Mock private RejoinBlockIdentifierHasher identifierHasher;
    @Mock private AccountIdentityGuard guard;

    private AccountIdentityGuardService service;

    @BeforeEach
    void setUp() {
        service = new AccountIdentityGuardService(guardRepository, identifierHasher);
    }

    @Test
    void lockPhone_hashesOnceThenUpsertsAndLocksTheSameIdentity() {
        when(identifierHasher.hashPhone("010-1234-5678")).thenReturn(PHONE_IDENTIFIER);
        when(guardRepository.findByIdentityForUpdate(
                        PHONE_IDENTIFIER.type(),
                        PHONE_IDENTIFIER.hash(),
                        PHONE_IDENTIFIER.keyVersion()))
                .thenReturn(Optional.of(guard));

        RejoinBlockIdentifier result = service.lockPhone("010-1234-5678", NOW);

        assertThat(result).isEqualTo(PHONE_IDENTIFIER);
        verify(identifierHasher).hashPhone("010-1234-5678");
        verify(guardRepository)
                .upsert("PHONE", PHONE_IDENTIFIER.hash(), PHONE_IDENTIFIER.keyVersion(), NOW);
        verify(guardRepository)
                .findByIdentityForUpdate(
                        PHONE_IDENTIFIER.type(),
                        PHONE_IDENTIFIER.hash(),
                        PHONE_IDENTIFIER.keyVersion());
    }
}
