package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.EmailVerification;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EmailVerificationCleanupRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 6, 45);

    @Autowired private EmailVerificationRepository emailVerificationRepository;

    @Test
    void deleteAllCreatedAtOrBefore_deletesAllOldStatusesAndKeepsRecentRows() {
        EmailVerification unverified = saveOld("cleanup-unverified@example.com");
        EmailVerification verified = saveOld("cleanup-verified@example.com");
        verified.verify(NOW.minusHours(22));
        EmailVerification consumed = saveOld("cleanup-consumed@example.com");
        consumed.verify(NOW.minusHours(22));
        consumed.consume(NOW.minusHours(21));
        EmailVerification recent = saveRecent();
        emailVerificationRepository.flush();

        int firstDeleted =
                emailVerificationRepository.deleteAllCreatedAtOrBefore(NOW.minusHours(22));
        int secondDeleted =
                emailVerificationRepository.deleteAllCreatedAtOrBefore(NOW.minusHours(22));

        assertThat(firstDeleted).isEqualTo(3);
        assertThat(secondDeleted).isZero();
        assertThat(emailVerificationRepository.existsById(unverified.getId())).isFalse();
        assertThat(emailVerificationRepository.existsById(verified.getId())).isFalse();
        assertThat(emailVerificationRepository.existsById(consumed.getId())).isFalse();
        assertThat(emailVerificationRepository.existsById(recent.getId())).isTrue();
    }

    private EmailVerification saveOld(String email) {
        return emailVerificationRepository.save(verification(email, NOW.minusHours(22)));
    }

    private EmailVerification saveRecent() {
        return emailVerificationRepository.save(
                verification("cleanup-recent@example.com", NOW.minusHours(21).minusMinutes(59)));
    }

    private EmailVerification verification(String email, LocalDateTime createdAt) {
        return EmailVerification.create(
                email,
                UUID.randomUUID().toString(),
                "123456",
                createdAt.plusMinutes(10),
                createdAt);
    }
}
