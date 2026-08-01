package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class KakaoUnlinkTaskTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 5, 25, 56, 123_456_000);

    @Test
    void pending_initializesOnlyPendingTaskFieldsWithCapturedTime() {
        SocialAccount socialAccount = new SocialAccount();

        KakaoUnlinkTask task = KakaoUnlinkTask.pending(socialAccount, 3L, NOW);

        assertThat(task.getSocialAccount()).isSameAs(socialAccount);
        assertThat(task.getGeneration()).isEqualTo(3L);
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(task.getRetryCycle()).isZero();
        assertThat(task.getAttemptCount()).isZero();
        assertThat(task.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(task.getCreatedAt()).isEqualTo(NOW);
        assertThat(task.getUpdatedAt()).isEqualTo(NOW);
        assertThat(task.getLastAttemptAt()).isNull();
        assertThat(task.getClaimToken()).isNull();
        assertThat(task.getClaimedBy()).isNull();
        assertThat(task.getClaimedAt()).isNull();
        assertThat(task.getLeaseExpiresAt()).isNull();
        assertThat(task.getLastHttpStatus()).isNull();
        assertThat(task.getLastKakaoCode()).isNull();
        assertThat(task.getLastErrorType()).isNull();
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void reservation_incrementsImmediatelyBeforeExternalAttempt() {
        KakaoUnlinkTask task = KakaoUnlinkTask.pending(new SocialAccount(), 1L, NOW);
        LocalDateTime leaseExpiresAt = NOW.plusMinutes(2);
        task.claim("claim-token", "worker-1", NOW, leaseExpiresAt);

        int attemptCount =
                task.reserveAttempt("claim-token", NOW.plusSeconds(1), NOW.plusSeconds(1), 12);

        assertThat(attemptCount).isOne();
        assertThat(task.getAttemptCount()).isOne();
        assertThat(task.getLastAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PROCESSING);
    }

    @Test
    void reservation_rejectsWrongTokenExpiredLeaseAndExhaustedBudget() {
        KakaoUnlinkTask task = KakaoUnlinkTask.pending(new SocialAccount(), 1L, NOW);
        task.claim("claim-token", "worker-1", NOW, NOW.plusSeconds(2));

        assertThatThrownBy(
                        () ->
                                task.reserveAttempt(
                                        "other-token", NOW.plusSeconds(1), NOW.plusSeconds(1), 12))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                task.reserveAttempt(
                                        "claim-token", NOW.plusSeconds(2), NOW.plusSeconds(2), 12))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                task.reserveAttempt(
                                        "claim-token", NOW.plusSeconds(1), NOW.plusSeconds(1), 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationDeadTask_startsNewRetryCycleOnlyOnExplicitResume() {
        KakaoUnlinkTask task = KakaoUnlinkTask.pending(new SocialAccount(), 1L, NOW);
        task.claim("claim-token", "worker-1", NOW, NOW.plusMinutes(2));
        task.reserveAttempt("claim-token", NOW.plusSeconds(1), NOW.plusSeconds(1), 12);
        task.dead(
                "claim-token",
                NOW.plusSeconds(2),
                NOW.plusSeconds(2),
                401,
                -401,
                KakaoUnlinkTaskErrorType.CONFIGURATION);

        task.startNewRetryCycle(NOW.plusHours(1));

        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(task.getRetryCycle()).isOne();
        assertThat(task.getAttemptCount()).isZero();
        assertThat(task.getNextAttemptAt()).isEqualTo(NOW.plusHours(1));
        assertThat(task.getLastErrorType()).isNull();
    }

    @Test
    void pending_rejectsMissingSocialAccount() {
        assertThatThrownBy(() -> KakaoUnlinkTask.pending(null, 1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pending_rejectsNonPositiveGeneration() {
        assertThatThrownBy(() -> KakaoUnlinkTask.pending(new SocialAccount(), 0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pending_rejectsMissingTime() {
        assertThatThrownBy(() -> KakaoUnlinkTask.pending(new SocialAccount(), 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void status_containsWorkerLifecycleStates() {
        assertThat(KakaoUnlinkTaskStatus.values())
                .containsExactly(
                        KakaoUnlinkTaskStatus.PENDING,
                        KakaoUnlinkTaskStatus.PROCESSING,
                        KakaoUnlinkTaskStatus.SUCCEEDED,
                        KakaoUnlinkTaskStatus.DEAD,
                        KakaoUnlinkTaskStatus.STALE);
    }

    @Test
    void task_doesNotCopyProviderIdentifiersOrRawPayloads() {
        assertThat(Arrays.stream(KakaoUnlinkTask.class.getDeclaredFields()).map(Field::getName))
                .noneMatch(
                        fieldName ->
                                fieldName.toLowerCase(Locale.ROOT).contains("provideruser")
                                        || fieldName.toLowerCase(Locale.ROOT).contains("ciphertext")
                                        || fieldName
                                                .toLowerCase(Locale.ROOT)
                                                .contains("authorization")
                                        || fieldName
                                                .toLowerCase(Locale.ROOT)
                                                .contains("rawresponse")
                                        || fieldName
                                                .toLowerCase(Locale.ROOT)
                                                .contains("requestbody"));
    }
}
