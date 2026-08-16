package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PhoneVerificationCleanupRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 6, 45);

    @Autowired private PhoneVerificationRepository phoneVerificationRepository;

    @Test
    void deleteAllCreatedAtOrBefore_deletesAllOldStatusesAndKeepsRecentRows() {
        PhoneVerification unverified = saveOld(PhoneVerificationPurpose.SIGNUP);
        PhoneVerification verified = saveOld(PhoneVerificationPurpose.FIND_ACCOUNT);
        verified.verify(NOW.minusHours(22));
        PhoneVerification consumed = saveOld(PhoneVerificationPurpose.RESET_PASSWORD);
        consumed.verify(NOW.minusHours(22));
        consumed.consume(NOW.minusHours(21));
        PhoneVerification recent = saveRecent();
        phoneVerificationRepository.flush();

        int firstDeleted =
                phoneVerificationRepository.deleteAllCreatedAtOrBefore(NOW.minusHours(22));
        int secondDeleted =
                phoneVerificationRepository.deleteAllCreatedAtOrBefore(NOW.minusHours(22));

        assertThat(firstDeleted).isEqualTo(3);
        assertThat(secondDeleted).isZero();
        assertThat(phoneVerificationRepository.existsById(unverified.getId())).isFalse();
        assertThat(phoneVerificationRepository.existsById(verified.getId())).isFalse();
        assertThat(phoneVerificationRepository.existsById(consumed.getId())).isFalse();
        assertThat(phoneVerificationRepository.existsById(recent.getId())).isTrue();
    }

    private PhoneVerification saveOld(PhoneVerificationPurpose purpose) {
        return phoneVerificationRepository.save(verification(purpose, NOW.minusHours(22)));
    }

    private PhoneVerification saveRecent() {
        return phoneVerificationRepository.save(
                verification(PhoneVerificationPurpose.SIGNUP, NOW.minusHours(21).minusMinutes(59)));
    }

    private PhoneVerification verification(
            PhoneVerificationPurpose purpose, LocalDateTime createdAt) {
        return PhoneVerification.create(
                UUID.randomUUID().toString(),
                "010" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000),
                purpose,
                "GATHER-CLEANUP01",
                createdAt.plusMinutes(5),
                createdAt);
    }
}
