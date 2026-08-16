package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.PhoneVerificationStatus;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PhoneVerificationPurposeIntegrationTest {

    private static final String PHONE_NUMBER = "01095550113";

    @Autowired private PhoneVerificationTransactionService transactionService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;

    @BeforeEach
    void setUp() {
        userRepository.save(
                User.create(
                        "목적회원",
                        LocalDate.of(2000, 1, 1),
                        Gender.FEMALE,
                        PHONE_NUMBER,
                        "purpose-integration@example.com",
                        "encoded-password",
                        "목적검증",
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of()));
    }

    @Test
    @DisplayName("FIND_ACCOUNT는 이미 가입된 전화번호도 VERIFIED로 전환한다")
    void verify_findAccount_allowsRegisteredPhone() {
        PhoneVerification verification = save(PhoneVerificationPurpose.FIND_ACCOUNT);

        assertThat(transactionService.verify(verification.getVerificationId()))
                .isEqualTo(PhoneVerificationStatus.VERIFIED);
        assertThat(verification.isVerified()).isTrue();
    }

    @Test
    @DisplayName("RESET_PASSWORD는 이미 가입된 전화번호도 VERIFIED로 전환한다")
    void verify_resetPassword_allowsRegisteredPhone() {
        PhoneVerification verification = save(PhoneVerificationPurpose.RESET_PASSWORD);

        assertThat(transactionService.verify(verification.getVerificationId()))
                .isEqualTo(PhoneVerificationStatus.VERIFIED);
        assertThat(verification.isVerified()).isTrue();
    }

    @Test
    @DisplayName("SIGNUP은 기존과 동일하게 이미 가입된 전화번호를 거부한다")
    void verify_signup_rejectsRegisteredPhone() {
        PhoneVerification verification = save(PhoneVerificationPurpose.SIGNUP);

        assertThatThrownBy(() -> transactionService.verify(verification.getVerificationId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_PHONE_NUMBER));
        assertThat(verification.isVerified()).isFalse();
    }

    private PhoneVerification save(PhoneVerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        return phoneVerificationRepository.save(
                PhoneVerification.create(
                        UUID.randomUUID().toString(),
                        PHONE_NUMBER,
                        purpose,
                        "GATHER-PURPOSE001",
                        now.plusMinutes(5),
                        now.minusMinutes(1)));
    }
}
