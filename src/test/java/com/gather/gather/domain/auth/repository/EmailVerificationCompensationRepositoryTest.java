package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.EmailVerification;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 발송 실패 보상 CAS가 자기 세대에만 적용되는지 실제 @Version 증가로 검증한다.
 *
 * <p>보상은 커밋 이후 별도 트랜잭션에서 실행되므로, 그 사이 다른 요청이 행을 바꾸면 보상이 최신 상태를 되돌려 버릴 수 있다. 스레드 경쟁 대신 version을 실제로
 * 증가시킨 뒤 낡은 version으로 호출해 결정적으로 확인한다.
 */
@SpringBootTest
class EmailVerificationCompensationRepositoryTest {

    private static final String EMAIL = "compensation-cas-test@example.com";

    private static final String PREVIOUS_VERIFICATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PREVIOUS_CODE_HASH = "a".repeat(64);
    private static final LocalDateTime PREVIOUS_CREATED_AT = LocalDateTime.of(2026, 8, 18, 9, 0);

    private static final String LATEST_VERIFICATION_ID = "22222222-2222-2222-2222-222222222222";
    private static final String LATEST_CODE_HASH = "b".repeat(64);
    private static final LocalDateTime LATEST_CREATED_AT = PREVIOUS_CREATED_AT.plusMinutes(5);

    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM email_verification WHERE email = ?", EMAIL);
    }

    @Test
    @DisplayName("낡은 version으로 복원하면 0건을 반환하고 최신 세대의 어떤 값도 덮어쓰지 않는다")
    void restoreAfterFailedResend_staleVersion_keepsLatestStateIntact() {
        Long id = saveInitialVerification();
        Long staleVersion = emailVerificationRepository.findById(id).orElseThrow().getVersion();

        advanceToLatestGeneration(id);

        EmailVerification latest = emailVerificationRepository.findById(id).orElseThrow();
        assertThat(latest.getVersion()).isGreaterThan(staleVersion);

        int affectedRows =
                emailVerificationRepository.restoreAfterFailedResend(
                        id,
                        staleVersion,
                        PREVIOUS_VERIFICATION_ID,
                        PREVIOUS_CODE_HASH,
                        false,
                        PREVIOUS_CREATED_AT.plusMinutes(10),
                        null,
                        null,
                        PREVIOUS_CREATED_AT,
                        1,
                        0);

        assertThat(affectedRows).isZero();

        EmailVerification reloaded = emailVerificationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getVerificationId()).isEqualTo(LATEST_VERIFICATION_ID);
        assertThat(reloaded.getCodeHash()).isEqualTo(LATEST_CODE_HASH);
        assertThat(reloaded.isVerified()).isTrue();
        assertThat(reloaded.getExpiresAt()).isEqualTo(LATEST_CREATED_AT.plusMinutes(10));
        assertThat(reloaded.getVerifiedAt()).isEqualTo(LATEST_CREATED_AT.plusMinutes(1));
        assertThat(reloaded.getConsumedAt()).isEqualTo(LATEST_CREATED_AT.plusMinutes(2));
        assertThat(reloaded.getCreatedAt()).isEqualTo(LATEST_CREATED_AT);
        assertThat(reloaded.getDailySendCount()).isEqualTo(2);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getVersion()).isEqualTo(latest.getVersion());
    }

    private Long saveInitialVerification() {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status ->
                                emailVerificationRepository
                                        .saveAndFlush(
                                                EmailVerification.create(
                                                        EMAIL,
                                                        PREVIOUS_VERIFICATION_ID,
                                                        PREVIOUS_CODE_HASH,
                                                        PREVIOUS_CREATED_AT.plusMinutes(10),
                                                        PREVIOUS_CREATED_AT))
                                        .getId());
    }

    // 복원 대상 컬럼이 모두 직전 세대와 다른 값이 되도록 갱신해, 잘못된 복원이 일어나면 반드시 드러나게 한다.
    private void advanceToLatestGeneration(Long id) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            EmailVerification found =
                                    emailVerificationRepository.findById(id).orElseThrow();
                            found.refresh(
                                    LATEST_VERIFICATION_ID,
                                    LATEST_CODE_HASH,
                                    LATEST_CREATED_AT.plusMinutes(10),
                                    LATEST_CREATED_AT);
                            found.increaseAttempt();
                            found.verify(LATEST_CREATED_AT.plusMinutes(1));
                            found.consume(LATEST_CREATED_AT.plusMinutes(2));
                        });
    }
}
