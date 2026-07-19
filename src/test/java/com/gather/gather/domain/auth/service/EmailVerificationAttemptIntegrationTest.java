package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 코드 입력 실패 시 attemptCount 증가가 예외 발생에도 롤백되지 않고 커밋되는지 검증한다. 단위 테스트로는 실제 트랜잭션 경계를 재현할 수 없어
 * noRollbackFor 설정의 실효를 확인하려면 실제 트랜잭션이 필요하다.
 */
@SpringBootTest
class EmailVerificationAttemptIntegrationTest {

    private static final String EMAIL = "reauth-attempt-test@example.com";

    @Autowired private AuthService authService;
    @Autowired private EmailVerificationRepository emailVerificationRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        emailVerificationRepository
                .findByEmail(EMAIL)
                .ifPresent(emailVerificationRepository::delete);
    }

    @Test
    @DisplayName("틀린 코드 입력 시 예외가 나도 시도 횟수 증가가 커밋되어 남는다")
    void wrongCode_persistsAttemptCount_despiteException() {
        emailVerificationRepository.saveAndFlush(
                EmailVerification.create(EMAIL, "123456", LocalDateTime.now().plusMinutes(10)));

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(EMAIL, "000000")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));

        EmailVerification reloaded = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
    }
}
