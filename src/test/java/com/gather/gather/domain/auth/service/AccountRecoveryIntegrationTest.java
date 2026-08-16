package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AccountRecoveryIntegrationTest {

    @Autowired private AccountRecoveryService accountRecoveryService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private Clock clock;

    @Test
    @DisplayName("인증 row의 전화번호로 ACTIVE 이메일 계정을 찾아 인증을 소비한다")
    void recoverEmail_returnsEmailAccountAndConsumesVerification() {
        String phoneNumber = "01095550101";
        User user =
                userRepository.save(emailUser(phoneNumber, "recovery-email@example.com", "복구메일"));
        PhoneVerification verification = saveVerified(phoneNumber);

        var response =
                accountRecoveryService.recoverEmail(
                        new AccountRecoveryRequest(
                                UUID.fromString(verification.getVerificationId())));

        assertThat(response.loginType()).isEqualTo(AccountLoginType.EMAIL);
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(verification.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACTIVE 카카오 전용 계정은 이메일 없이 KAKAO로 반환하고 인증을 소비한다")
    void recoverEmail_returnsLinkedKakaoOnlyAccount() {
        String phoneNumber = "01095550102";
        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.save(socialUser(phoneNumber, "복구카카오"));
        socialAccountRepository.save(
                SocialAccount.createLinked(
                        user,
                        SocialProvider.KAKAO,
                        "account-recovery-provider",
                        "a".repeat(64),
                        1,
                        new EncryptedProviderUserId("account-recovery-ciphertext", 1),
                        now));
        PhoneVerification verification = saveVerified(phoneNumber);

        var response =
                accountRecoveryService.recoverEmail(
                        new AccountRecoveryRequest(
                                UUID.fromString(verification.getVerificationId())));

        assertThat(response.loginType()).isEqualTo(AccountLoginType.KAKAO);
        assertThat(response.email()).isNull();
        assertThat(verification.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("계정이 없어도 인증 소비를 유지한 채 ACCOUNT_NOT_FOUND를 반환한다")
    void recoverEmail_consumesVerificationBeforeAccountNotFoundError() {
        PhoneVerification verification = saveVerified("01095550103");

        assertThatThrownBy(
                        () ->
                                accountRecoveryService.recoverEmail(
                                        new AccountRecoveryRequest(
                                                UUID.fromString(verification.getVerificationId()))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));

        assertThat(verification.getConsumedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"SUSPENDED", "WITHDRAWAL_PENDING", "WITHDRAWN"})
    @DisplayName("ACTIVE가 아닌 계정은 상태를 노출하지 않고 인증을 소비한 뒤 ACCOUNT_NOT_FOUND로 처리한다")
    void recoverEmail_rejectsInactiveAccountAndConsumesVerification(UserStatus status) {
        String phoneNumber =
                switch (status) {
                    case SUSPENDED -> "01095550107";
                    case WITHDRAWAL_PENDING -> "01095550108";
                    case WITHDRAWN -> "01095550109";
                    default -> throw new IllegalArgumentException("테스트 대상 상태가 아닙니다.");
                };
        User user = emailUser(phoneNumber, status.name().toLowerCase() + "@example.com", "상태복구");
        if (status == UserStatus.SUSPENDED) {
            ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        } else if (status == UserStatus.WITHDRAWAL_PENDING) {
            user.requestWithdrawal(WithdrawalReason.SELF, LocalDateTime.now(clock));
        } else {
            user.withdraw(WithdrawalReason.SELF, LocalDateTime.now(clock));
        }
        userRepository.save(user);
        PhoneVerification verification = saveVerified(phoneNumber);

        assertAccountNotFound(verification);

        assertThat(verification.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("email만 있고 password가 없는 비정상 계정을 KAKAO로 분류하지 않는다")
    void recoverEmail_rejectsEmailWithoutPassword() {
        String phoneNumber = "01095550110";
        User user = emailUser(phoneNumber, "partial-email@example.com", "부분이메일");
        ReflectionTestUtils.setField(user, "password", null);
        userRepository.save(user);
        PhoneVerification verification = saveVerified(phoneNumber);

        assertAccountNotFound(verification);

        assertThat(verification.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("password만 있고 email이 없는 비정상 계정을 KAKAO로 분류하지 않는다")
    void recoverEmail_rejectsPasswordWithoutEmail() {
        String phoneNumber = "01095550111";
        User user = emailUser(phoneNumber, "partial-password@example.com", "부분암호");
        ReflectionTestUtils.setField(user, "email", null);
        userRepository.save(user);
        PhoneVerification verification = saveVerified(phoneNumber);

        assertAccountNotFound(verification);

        assertThat(verification.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("credential과 LINKED KAKAO가 모두 없는 계정을 KAKAO로 분류하지 않는다")
    void recoverEmail_rejectsSocialAccountWithoutLinkedKakao() {
        String phoneNumber = "01095550112";
        userRepository.save(socialUser(phoneNumber, "연결없음"));
        PhoneVerification verification = saveVerified(phoneNumber);

        assertAccountNotFound(verification);

        assertThat(verification.getConsumedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = SocialAccountLinkStatus.class,
            names = {"UNLINK_PENDING", "UNLINKED"})
    @DisplayName("연결 해제 중이거나 완료된 카카오 계정은 KAKAO로 분류하지 않는다")
    void recoverEmail_rejectsUnlinkedKakaoAccount(SocialAccountLinkStatus linkStatus) {
        String phoneNumber =
                linkStatus == SocialAccountLinkStatus.UNLINK_PENDING
                        ? "01095550113"
                        : "01095550114";
        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.save(socialUser(phoneNumber, "해제상태"));
        SocialAccount socialAccount =
                SocialAccount.createLinked(
                        user,
                        SocialProvider.KAKAO,
                        "account-recovery-" + linkStatus.name().toLowerCase(),
                        (linkStatus == SocialAccountLinkStatus.UNLINK_PENDING ? "b" : "c")
                                .repeat(64),
                        1,
                        new EncryptedProviderUserId(
                                "account-recovery-" + linkStatus.name().toLowerCase(), 1),
                        now);
        socialAccount.markUnlinkPending(now.plusSeconds(1));
        if (linkStatus == SocialAccountLinkStatus.UNLINKED) {
            socialAccount.markUnlinked(now.plusSeconds(2));
        }
        socialAccountRepository.save(socialAccount);
        PhoneVerification verification = saveVerified(phoneNumber);

        assertAccountNotFound(verification);

        assertThat(verification.getConsumedAt()).isNotNull();
    }

    private PhoneVerification saveVerified(String phoneNumber) {
        LocalDateTime now = LocalDateTime.now(clock);
        PhoneVerification verification =
                PhoneVerification.create(
                        UUID.randomUUID().toString(),
                        phoneNumber,
                        PhoneVerificationPurpose.FIND_ACCOUNT,
                        "GATHER-RECOVERY02",
                        now.plusMinutes(5),
                        now.minusMinutes(1));
        verification.verify(now.minusMinutes(1));
        return phoneVerificationRepository.save(verification);
    }

    private void assertAccountNotFound(PhoneVerification verification) {
        assertThatThrownBy(
                        () ->
                                accountRecoveryService.recoverEmail(
                                        new AccountRecoveryRequest(
                                                UUID.fromString(verification.getVerificationId()))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private User emailUser(String phoneNumber, String email, String nickname) {
        return User.create(
                "복구회원",
                LocalDate.of(2000, 1, 1),
                com.gather.gather.domain.auth.entity.Gender.FEMALE,
                phoneNumber,
                email,
                "encoded-password",
                nickname,
                null,
                true,
                true,
                false,
                null,
                List.of());
    }

    private User socialUser(String phoneNumber, String nickname) {
        return User.createSocial(
                "복구회원",
                LocalDate.of(2000, 1, 1),
                com.gather.gather.domain.auth.entity.Gender.FEMALE,
                phoneNumber,
                nickname,
                null,
                true,
                true,
                false,
                null,
                List.of());
    }
}
