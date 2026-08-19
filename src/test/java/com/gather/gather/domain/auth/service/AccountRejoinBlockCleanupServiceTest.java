package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRejoinBlockCleanupServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-04-30T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 9, 0);

    @Mock private AccountRejoinBlockRepository blockRepository;

    private AccountRejoinBlockCleanupService service;

    @BeforeEach
    void setUp() {
        service = new AccountRejoinBlockCleanupService(blockRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("주입된 Clock의 현재 시각을 기준으로 파기하고 삭제 건수를 반환한다")
    void cleanupRetentionExpiredBlocks_usesInjectedClockAndReturnsDeletedCount() {
        when(blockRepository.deleteAllRetentionExpired(NOW)).thenReturn(3);

        int deleted = service.cleanupRetentionExpiredBlocks();

        assertThat(deleted).isEqualTo(3);
        verify(blockRepository).deleteAllRetentionExpired(NOW);
    }
}
