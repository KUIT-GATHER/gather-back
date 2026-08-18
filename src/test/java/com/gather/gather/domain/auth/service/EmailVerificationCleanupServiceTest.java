package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationCleanupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 6, 45);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock private EmailVerificationRepository emailVerificationRepository;

    @Test
    void cleanupOverdueVerifications_deletesRowsAtTwentyTwoHourCutoff() {
        LocalDateTime cutoff = NOW.minusHours(22);
        when(emailVerificationRepository.deleteAllCreatedAtOrBefore(cutoff)).thenReturn(4);
        EmailVerificationCleanupService service =
                new EmailVerificationCleanupService(emailVerificationRepository, CLOCK);

        assertThat(service.cleanupOverdueVerifications()).isEqualTo(4);

        verify(emailVerificationRepository).deleteAllCreatedAtOrBefore(cutoff);
    }
}
