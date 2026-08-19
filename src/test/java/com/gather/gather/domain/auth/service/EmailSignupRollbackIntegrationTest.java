package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
    @MockitoSpyBean private SignupValidator signupValidator;

    private Long activityRegionId;
    private UUID phoneVerificationId;
    private UUID emailVerificationId;
    private Long conflictingUserId;

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
                            emailVerificationId = UUID.randomUUID();
                            EmailVerification verification =
                                    EmailVerification.create(
                                            EMAIL,
                                            emailVerificationId.toString(),
                                            "a".repeat(64),
                                            now.plusDays(1),
                                            now);
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
                            if (conflictingUserId != null) {
                                userRepository.deleteById(conflictingUserId);
                            }
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
    @DisplayName("Refresh Token 저장 실패는 두 인증 소비를 rollback하고 같은 ID 재시도를 허용한다")
    void signup_whenRefreshTokenSaveFails_rollsBackProofsAndAllowsRetry() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenThrow(new DataIntegrityViolationException("forced refresh token failure"));

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("forced refresh token failure");

        assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(
                        emailVerificationRepository
                                .findByVerificationId(emailVerificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNull();
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(phoneVerificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNull();

        org.mockito.Mockito.reset(refreshTokenRepository);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(signupRequest());

        assertThat(userRepository.findByEmail(EMAIL)).isPresent();
        assertThat(emailVerificationRepository.findByVerificationId(emailVerificationId.toString()))
                .isEmpty();
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(phoneVerificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("User 저장 unique 제약 실패는 이메일 인증 DELETE를 rollback하고 같은 ID 재시도를 허용한다")
    void signup_whenUserSaveFails_rollsBackEmailVerificationAndAllowsRetry() {
        doAnswer(
                        invocation -> {
                            createCommittedNicknameConflict();
                            return invocation.callRealMethod();
                        })
                .when(signupValidator)
                .findActivityRegion(activityRegionId);

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_NICKNAME));

        assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(emailVerificationRepository.findByVerificationId(emailVerificationId.toString()))
                .isPresent();
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(phoneVerificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNull();

        transactionTemplate()
                .executeWithoutResult(status -> userRepository.deleteById(conflictingUserId));
        conflictingUserId = null;
        reset(signupValidator);

        authService.signup(signupRequest());

        assertThat(userRepository.findByEmail(EMAIL)).isPresent();
        assertThat(emailVerificationRepository.findByVerificationId(emailVerificationId.toString()))
                .isEmpty();
    }

    private void createCommittedNicknameConflict() {
        conflictingUserId =
                transactionTemplate()
                        .execute(
                                status -> {
                                    Region activityRegion =
                                            regionRepository
                                                    .findById(activityRegionId)
                                                    .orElseThrow();
                                    User conflictingUser =
                                            userRepository.saveAndFlush(
                                                    User.create(
                                                            "충돌회원",
                                                            LocalDate.of(2001, 1, 2),
                                                            Gender.MALE,
                                                            "01095550003",
                                                            "email-signup-conflict@example.com",
                                                            "encoded-password",
                                                            "롤백검증",
                                                            null,
                                                            true,
                                                            true,
                                                            false,
                                                            activityRegion,
                                                            List.of(PostingCategory.WELFARE)));
                                    return conflictingUser.getId();
                                });
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(
                "롤백회원",
                LocalDate.of(2001, 1, 1),
                Gender.FEMALE,
                PHONE_NUMBER,
                phoneVerificationId,
                EMAIL,
                emailVerificationId,
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
