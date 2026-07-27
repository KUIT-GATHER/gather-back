package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    private static final Long USER_ID = 7L;
    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 7, 27, 12, 0);

    private User activeUser() {
        User user =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        "소개글",
                        true,
                        true,
                        false,
                        Region.create("강남구", 2, "11680", null),
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    @Test
    @DisplayName("탈퇴하면 상태·시각·사유가 기록된다")
    void withdraw_recordsStatusAndReason() {
        User user = activeUser();

        user.withdraw(WithdrawalReason.SELF, WITHDRAWN_AT);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isEqualTo(WITHDRAWN_AT);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
    }

    @Test
    @DisplayName("탈퇴 직후에는 개인정보가 그대로 남는다")
    void withdraw_keepsPersonalDataForGracePeriod() {
        User user = activeUser();

        user.withdraw(WithdrawalReason.KAKAO_UNLINK, WITHDRAWN_AT);

        assertThat(user.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(user.getNickname()).isEqualTo("길동");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("익명화하면 유니크 컬럼만 훼손되고 나머지는 보존된다")
    void anonymize_replacesUniqueColumnsOnly() {
        User user = activeUser();
        user.withdraw(WithdrawalReason.SELF, WITHDRAWN_AT);

        user.anonymize();

        assertThat(user.getPhoneNumber()).isEqualTo("wd_" + USER_ID);
        assertThat(user.getNickname()).isEqualTo("wd_" + USER_ID);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(user.getGender()).isEqualTo(Gender.MALE);
        assertThat(user.getWithdrawnAt()).isEqualTo(WITHDRAWN_AT);
    }

    @Test
    @DisplayName("익명화를 재수행해도 같은 값이라 재처리에 안전하다")
    void anonymize_isIdempotent() {
        User user = activeUser();
        user.withdraw(WithdrawalReason.SELF, WITHDRAWN_AT);
        user.anonymize();

        user.anonymize();

        assertThat(user.getPhoneNumber()).isEqualTo("wd_" + USER_ID);
        assertThat(user.getNickname()).isEqualTo("wd_" + USER_ID);
    }
}
