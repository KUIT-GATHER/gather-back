package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.dto.EmailVerificationSendRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(EmailVerificationRetentionIntegrationTest.MutableClockConfiguration.class)
class EmailVerificationRetentionIntegrationTest {

    private static final String EMAIL = "email-verification-retention@example.com";
    private static final Instant INITIAL_INSTANT = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired private AuthService authService;
    @Autowired private EmailVerificationCleanupService cleanupService;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private MutableClock clock;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        clock.setInstant(INITIAL_INSTANT);
        transactionTemplate()
                .executeWithoutResult(
                        status -> emailVerificationRepository.deleteAllByEmail(EMAIL));
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> emailVerificationRepository.deleteAllByEmail(EMAIL));
    }

    @Test
    void resend_refreshesRetentionTimestampAndCleanupKeepsVerification() {
        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));
        EmailVerification first = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();

        clock.advance(Duration.ofHours(21));
        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));

        EmailVerification resent = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(resent.getId()).isEqualTo(first.getId());
        assertThat(resent.getCreatedAt()).isEqualTo(clock.localDateTime());

        clock.advance(Duration.ofHours(1));

        assertThat(cleanupService.cleanupOverdueVerifications()).isZero();
        assertThat(emailVerificationRepository.findByEmail(EMAIL)).isPresent();
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @TestConfiguration
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock emailVerificationRetentionClock() {
            return new MutableClock(INITIAL_INSTANT);
        }
    }

    static class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        MutableClock(Instant initialInstant) {
            this.instant = new AtomicReference<>(initialInstant);
        }

        void setInstant(Instant nextInstant) {
            instant.set(nextInstant);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        java.time.LocalDateTime localDateTime() {
            return java.time.LocalDateTime.ofInstant(instant(), ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException(
                        "Only UTC is supported in this test clock.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
