package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AccountRecoveryRollbackIntegrationTest {

    private static final String PHONE_NUMBER = "01095550105";

    @Autowired private AccountRecoveryTransactionService transactionService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;
    @MockitoBean private AccountLoginTypeResolver accountLoginTypeResolver;

    private UUID verificationId;
    private Long userId;

    @BeforeEach
    void setUp() {
        verificationId = UUID.randomUUID();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            User user =
                                    userRepository.save(
                                            User.create(
                                                    "롤백회원",
                                                    LocalDate.of(2000, 1, 1),
                                                    com.gather.gather.domain.auth.entity.Gender
                                                            .FEMALE,
                                                    PHONE_NUMBER,
                                                    "recovery-rollback@example.com",
                                                    "encoded-password",
                                                    "롤백복구",
                                                    null,
                                                    true,
                                                    true,
                                                    false,
                                                    null,
                                                    List.of()));
                            userId = user.getId();
                            PhoneVerification verification =
                                    PhoneVerification.create(
                                            verificationId.toString(),
                                            PHONE_NUMBER,
                                            PhoneVerificationPurpose.FIND_ACCOUNT,
                                            "GATHER-RECOVERY04",
                                            now.plusMinutes(5),
                                            now.minusMinutes(1));
                            verification.verify(now.minusMinutes(1));
                            phoneVerificationRepository.save(verification);
                        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            phoneVerificationRepository
                                    .findByVerificationId(verificationId.toString())
                                    .ifPresent(phoneVerificationRepository::delete);
                            if (userId != null) {
                                userRepository.deleteById(userId);
                            }
                        });
    }

    @Test
    @DisplayName("계정 판정 중 시스템 오류가 발생하면 verification 소비를 롤백한다")
    void recoverEmail_rollsBackConsumptionOnUnexpectedFailure() {
        when(accountLoginTypeResolver.resolveForActiveAccount(
                        org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new IllegalStateException("classification failure"));

        assertThatThrownBy(() -> transactionService.recoverEmail(verificationId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(verificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNull();
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
