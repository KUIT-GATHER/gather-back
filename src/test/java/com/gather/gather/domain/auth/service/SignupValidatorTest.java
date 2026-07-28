package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.PhoneNumberUnavailableReason;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 전화번호 점유자 상태에 따른 분기 검증. 일반 가입과 소셜 가입이 이 검증을 공유하므로 여기 한 곳이 양쪽을 대표한다. */
@ExtendWith(MockitoExtension.class)
class SignupValidatorTest {

    private static final String PHONE_NUMBER = "01012345678";

    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;

    @Spy private WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy();

    @InjectMocks private SignupValidator signupValidator;

    private User user(UserStatus status, LocalDateTime withdrawnAt) {
        User user =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        PHONE_NUMBER,
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        null,
                        true,
                        true,
                        false,
                        Region.create("강남구", 2, "11680", null),
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(user, "id", 7L);
        if (withdrawnAt != null) {
            user.withdraw(WithdrawalReason.SELF, withdrawnAt);
        } else {
            ReflectionTestUtils.setField(user, "status", status);
        }
        return user;
    }

    @Test
    @DisplayName("쓰는 사람이 없으면 통과한다")
    void validatePhoneNumberNotDuplicated_whenUnused_passes() {
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER)).thenReturn(Optional.empty());

        assertThatCode(() -> signupValidator.preparePhoneNumberForSignup(PHONE_NUMBER))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("활성 회원이 쓰고 있으면 이미 사용 중 오류다")
    void validatePhoneNumberNotDuplicated_whenActiveUserHolds_throwsDuplicate() {
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, null)));

        assertThatThrownBy(() -> signupValidator.preparePhoneNumberForSignup(PHONE_NUMBER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_PHONE_NUMBER));
    }

    @Test
    @DisplayName("탈퇴자가 쥐고 있으면 기다리면 풀린다는 뜻의 재가입 유예 오류다")
    void validatePhoneNumberNotDuplicated_whenWithdrawnUserHolds_throwsCooldown() {
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER))
                .thenReturn(
                        Optional.of(user(UserStatus.WITHDRAWN, LocalDateTime.now().minusDays(1))));

        assertThatThrownBy(() -> signupValidator.preparePhoneNumberForSignup(PHONE_NUMBER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.WITHDRAWN_PHONE_NUMBER_COOLDOWN));
    }

    @Test
    @DisplayName("유예가 끝났어도 익명화 전이면 아직 번호를 쓸 수 없다")
    void phoneNumberUnavailableReason_afterGraceButNotAnonymized_isCooldown() {
        User withdrawn = user(UserStatus.WITHDRAWN, LocalDateTime.now().minusDays(30));

        assertThat(signupValidator.isPhoneNumberAvailable(withdrawn)).isTrue();
    }

    @Test
    void preparePhoneNumberForSignup_atSevenDaysAnonymizesAndFlushesHolder() {
        User withdrawn = user(UserStatus.WITHDRAWN, LocalDateTime.now().minusDays(7));
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER))
                .thenReturn(Optional.of(withdrawn));

        signupValidator.preparePhoneNumberForSignup(PHONE_NUMBER);

        assertThat(withdrawn.getPhoneNumber()).isEqualTo("wd_7");
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getNickname()).isEqualTo("wd_7");
        verify(userRepository).flush();
    }

    @Test
    @DisplayName("정지 회원은 탈퇴자가 아니므로 이미 사용 중으로 본다")
    void phoneNumberUnavailableReason_whenSuspended_isInUse() {
        assertThat(signupValidator.phoneNumberUnavailableReason(user(UserStatus.SUSPENDED, null)))
                .isEqualTo(PhoneNumberUnavailableReason.IN_USE);
    }
}
