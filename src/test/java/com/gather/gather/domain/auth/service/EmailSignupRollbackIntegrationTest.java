package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class EmailSignupRollbackIntegrationTest {

    private static final String EMAIL = "email-signup-rollback@example.com";
    private static final String PHONE_NUMBER = "01095550002";
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;
    @MockitoBean private RefreshTokenRepository refreshTokenRepository;

    private Long activityRegionId;
    private UUID phoneVerificationId;

    @BeforeEach
    void setUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            phoneVerificationId = UUID.randomUUID();
                            Region activityRegion =
                                    regionRepository.save(
                                            Region.create(
                                                    "email-signup-rollback",
                                                    2,
                                                    "email-signup-rollback-code",
                                                    null));
                            activityRegionId = activityRegion.getId();
                            EmailVerification verification =
                                    EmailVerification.create(EMAIL, "123456", now.plusDays(1));
                            verification.verify(now);
                            emailVerificationRepository.save(verification);
                            PhoneVerification phoneVerification =
                                    PhoneVerification.create(
                                            phoneVerificationId.toString(),
                                            PHONE_NUMBER,
                                            PhoneVerificationPurpose.SIGNUP,
                                            "GATHER-ROLLBACK01",
                                            now.plusMinutes(5),
                                            now.minusMinutes(1));
                            phoneVerification.verify(now.minusMinutes(1));
                            phoneVerificationRepository.save(phoneVerification);
                        });
    }

    @AfterEach
    void cleanUp() {
        RejoinBlockIdentifier identifier = identifierHasher.hashPhone(PHONE_NUMBER);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
                            emailVerificationRepository.deleteAllByEmail(EMAIL);
                            phoneVerificationRepository
                                    .findByVerificationId(phoneVerificationId.toString())
                                    .ifPresent(phoneVerificationRepository::delete);
                            jdbcTemplate.update(
                                    "delete from account_identity_guard where identity_type = 'PHONE' and key_version = ? and identity_hash = ?",
                                    identifier.keyVersion(),
                                    identifier.hash());
                            regionRepository.deleteById(activityRegionId);
                        });
    }

    @Test
    @DisplayName("Refresh Token 저장 실패는 신규 User 생성을 함께 rollback한다")
    void signup_whenRefreshTokenSaveFails_rollsBackUser() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenThrow(new DataIntegrityViolationException("forced refresh token failure"));

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("forced refresh token failure");

        assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(phoneVerificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNull();
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(
                "롤백회원",
                LocalDate.of(2001, 1, 1),
                Gender.FEMALE,
                PHONE_NUMBER,
                phoneVerificationId,
                EMAIL,
                "password1",
                "password1",
                "롤백검증",
                null,
                activityRegionId,
                List.of(PostingCategory.WELFARE),
                true,
                true,
                false);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
