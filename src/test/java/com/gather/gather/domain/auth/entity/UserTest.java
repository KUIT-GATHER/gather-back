package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    @Test
    void create_setsCreatedAtAndUpdatedAtToSameTime() {
        LocalDateTime before = LocalDateTime.now();

        User user = user(1L);

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(user.getCreatedAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void requestWithdrawal_changesActiveUserToPendingWithoutCompletionFields() {
        User user = user(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);

        user.requestWithdrawal(WithdrawalReason.SELF, now);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getWithdrawnAt()).isNull();
        assertThat(user.getAnonymizedAt()).isNull();
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void requestWithdrawal_allowsSuspendedUser() {
        User user = user(1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);

        user.requestWithdrawal(WithdrawalReason.ADMIN, now);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.ADMIN);
    }

    @Test
    void requestWithdrawal_keepsFirstReasonWhenAlreadyPending() {
        User user = user(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        user.requestWithdrawal(WithdrawalReason.SELF, now);

        user.requestWithdrawal(WithdrawalReason.ADMIN, now.plusDays(1));

        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void requestWithdrawal_rejectsWithdrawnUser() {
        User user = user(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        user.withdraw(WithdrawalReason.SELF, now);

        assertThatThrownBy(() -> user.requestWithdrawal(WithdrawalReason.ADMIN, now.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requestWithdrawal_rejectsNullReason() {
        User user = user(1L);

        assertThatThrownBy(() -> user.requestWithdrawal(null, LocalDateTime.of(2026, 7, 29, 12, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("탈퇴 사유는 필수입니다.");
    }

    @Test
    void requestWithdrawal_rejectsNullTime() {
        User user = user(1L);

        assertThatThrownBy(() -> user.requestWithdrawal(WithdrawalReason.SELF, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("탈퇴 시각은 필수입니다.");
    }

    @Test
    void withdraw_changesActiveUserToWithdrawnAndRecordsReasonAndTime() {
        User user = user(1L);
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 7, 29, 12, 0);

        user.withdraw(WithdrawalReason.SELF, withdrawnAt);

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getWithdrawnAt()).isEqualTo(withdrawnAt);
        assertThat(user.getUpdatedAt()).isEqualTo(withdrawnAt);
    }

    @Test
    void withdraw_keepsFirstReasonAndTime_whenCalledMoreThanOnce() {
        User user = user(1L);
        LocalDateTime firstWithdrawnAt = LocalDateTime.of(2026, 7, 29, 12, 0);

        user.withdraw(WithdrawalReason.SELF, firstWithdrawnAt);
        user.withdraw(WithdrawalReason.ADMIN, firstWithdrawnAt.plusDays(1));

        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getWithdrawnAt()).isEqualTo(firstWithdrawnAt);
        assertThat(user.getUpdatedAt()).isEqualTo(firstWithdrawnAt);
    }

    @Test
    void completePendingWithdrawal_recordsWithdrawalTimeAsUpdatedAt() {
        User user = user(1L);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 7, 29, 12, 0);
        user.requestWithdrawal(WithdrawalReason.SELF, requestedAt);
        LocalDateTime completedAt = requestedAt.plusDays(7);

        user.completePendingWithdrawal(completedAt);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isEqualTo(completedAt);
        assertThat(user.getUpdatedAt()).isEqualTo(completedAt);
    }

    @Test
    void completePendingWithdrawal_keepsUpdatedAt_whenAlreadyWithdrawn() {
        User user = user(1L);
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 7, 29, 12, 0);
        user.withdraw(WithdrawalReason.SELF, withdrawnAt);

        user.completePendingWithdrawal(withdrawnAt.plusDays(1));

        assertThat(user.getWithdrawnAt()).isEqualTo(withdrawnAt);
        assertThat(user.getUpdatedAt()).isEqualTo(withdrawnAt);
    }

    @Test
    void withdraw_rejectsNullReason() {
        User user = user(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);

        assertThatThrownBy(() -> user.withdraw(null, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("탈퇴 사유는 필수입니다.");
    }

    @Test
    void withdraw_rejectsNullTime() {
        User user = user(1L);

        assertThatThrownBy(() -> user.withdraw(WithdrawalReason.SELF, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("탈퇴 시각은 필수입니다.");
    }

    @Test
    void withdraw_allowsSuspendedUser() {
        User user = user(1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);

        user.withdraw(WithdrawalReason.ADMIN, now);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isEqualTo(now);
    }

    @Test
    void withdraw_rejectsPendingUser() {
        User user = user(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        user.requestWithdrawal(WithdrawalReason.SELF, now);

        assertThatThrownBy(() -> user.withdraw(WithdrawalReason.SELF, now.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anonymize_removesPersonalInformationAndKeepsRequiredConsentHistory() {
        User user = user(42L);
        user.changeProfileImageKey("profiles/42/image.jpg");
        LocalDateTime anonymizedAt = LocalDateTime.of(2026, 7, 29, 12, 30);
        user.withdraw(WithdrawalReason.SELF, anonymizedAt.minusMinutes(1));

        user.anonymize(anonymizedAt);

        assertThat(user.isAnonymized()).isTrue();
        assertThat(user.getAnonymizedAt()).isEqualTo(anonymizedAt);
        assertThat(user.getName()).isNull();
        assertThat(user.getBirthDate()).isNull();
        assertThat(user.getGender()).isNull();
        assertThat(user.getPhoneNumber()).isEqualTo("wdp_42");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getNickname()).isEqualTo("wdn_42");
        assertThat(user.getIntroduction()).isNull();
        assertThat(user.getProfileImageKey()).isNull();
        assertThat(user.getActivityRegion()).isNull();
        assertThat(user.getInterestCategories()).isEmpty();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.isMarketingAgreed()).isFalse();
        assertThat(user.isServiceTermsAgreed()).isTrue();
        assertThat(user.isPrivacyPolicyAgreed()).isTrue();
        assertThat(user.getUpdatedAt()).isEqualTo(anonymizedAt);
    }

    @Test
    void anonymize_createsDifferentUniqueValuesForDifferentUsers() {
        User firstUser = user(1L);
        User secondUser = user(2L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        firstUser.withdraw(WithdrawalReason.SELF, now);
        secondUser.withdraw(WithdrawalReason.SELF, now);

        firstUser.anonymize(now);
        secondUser.anonymize(now);

        assertThat(firstUser.getPhoneNumber()).isNotEqualTo(secondUser.getPhoneNumber());
        assertThat(firstUser.getNickname()).isNotEqualTo(secondUser.getNickname());
    }

    @Test
    void anonymize_keepsFirstAnonymizedTime_whenCalledMoreThanOnce() {
        User user = user(1L);
        LocalDateTime firstAnonymizedAt = LocalDateTime.of(2026, 7, 29, 12, 0);
        user.withdraw(WithdrawalReason.SELF, firstAnonymizedAt.minusMinutes(1));

        user.anonymize(firstAnonymizedAt);
        user.anonymize(firstAnonymizedAt.plusDays(1));

        assertThat(user.getAnonymizedAt()).isEqualTo(firstAnonymizedAt);
        assertThat(user.getPhoneNumber()).isEqualTo("wdp_1");
        assertThat(user.getNickname()).isEqualTo("wdn_1");
        assertThat(user.getUpdatedAt()).isEqualTo(firstAnonymizedAt);
    }

    @Test
    void anonymize_rejectsTransientUserWithoutId() {
        User user = user(null);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        user.withdraw(WithdrawalReason.SELF, now);

        assertThatThrownBy(() -> user.anonymize(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("영속화되지 않은 사용자는 익명화할 수 없습니다.");
    }

    @Test
    void anonymize_rejectsActiveUser() {
        User user = user(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);

        assertThatThrownBy(() -> user.anonymize(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("탈퇴한 사용자만 익명화할 수 있습니다.");
    }

    @Test
    void anonymize_rejectsNullTime() {
        User user = user(1L);
        user.withdraw(WithdrawalReason.SELF, LocalDateTime.of(2026, 7, 29, 12, 0));

        assertThatThrownBy(() -> user.anonymize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("익명화 시각은 필수입니다.");
    }

    @Test
    void changePassword_refreshesUpdatedAtWithoutTouchingCreatedAt() {
        User user = user(1L);
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime staleUpdatedAt = createdAt.minusDays(1);
        ReflectionTestUtils.setField(user, "updatedAt", staleUpdatedAt);

        user.changePassword("new-encoded-password");

        assertThat(user.getUpdatedAt()).isAfter(staleUpdatedAt);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void changeProfileImageKey_refreshesUpdatedAtWithoutTouchingCreatedAt() {
        User user = user(1L);
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime staleUpdatedAt = createdAt.minusDays(1);
        ReflectionTestUtils.setField(user, "updatedAt", staleUpdatedAt);

        user.changeProfileImageKey("profiles/1/image.jpg");

        assertThat(user.getUpdatedAt()).isAfter(staleUpdatedAt);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void updateProfile_refreshesUpdatedAtWithoutTouchingCreatedAt() {
        User user = user(1L);
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime staleUpdatedAt = createdAt.minusDays(1);
        ReflectionTestUtils.setField(user, "updatedAt", staleUpdatedAt);

        user.updateProfile(
                "김수정",
                "수정",
                "수정된 소개",
                LocalDate.of(1999, 12, 31),
                Gender.FEMALE,
                user.getActivityRegion(),
                List.of(PostingCategory.ENVIRONMENT));

        assertThat(user.getUpdatedAt()).isAfter(staleUpdatedAt);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }

    private User user(Long id) {
        Region region = Region.create("테스트구", 2, "test-region-" + id, null);
        User user =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "user@example.com",
                        "encoded-password",
                        "길동",
                        "소개",
                        true,
                        true,
                        true,
                        region,
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
