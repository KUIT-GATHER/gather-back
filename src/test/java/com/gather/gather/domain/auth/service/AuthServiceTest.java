package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationSendRequest;
import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.PhoneNumberAvailabilityRequest;
import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailSender emailSender;
    @Mock private TokenProvider tokenProvider;
    @Mock private LockedTokenIssuanceService lockedTokenIssuanceService;
    @Mock private AccountRejoinBlockService accountRejoinBlockService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // SignupValidator·TokenIssuer·LoginPolicy는 mock이 아니라 실물을 쓴다. 검증·토큰 발급 로직이 AuthService에서
        // 분리됐을 뿐 동작은 그대로여야 하므로, mock으로 대체하면 이 테스트들의 검출력이 사라진다.
        authService =
                new AuthService(
                        userRepository,
                        emailVerificationRepository,
                        refreshTokenRepository,
                        passwordEncoder,
                        emailSender,
                        tokenProvider,
                        new TokenIssuer(tokenProvider, refreshTokenRepository),
                        lockedTokenIssuanceService,
                        new SignupValidator(
                                userRepository, regionRepository, new PhoneNumberNormalizer()),
                        new LoginPolicy(),
                        accountRejoinBlockService,
                        Clock.fixed(Instant.parse("2026-07-31T05:25:56.123456Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("새 이메일이면 인증 코드를 발송하고 저장한다")
    void sendEmailVerificationCode_newEmail_sendsAndSaves() {
        String email = "new@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);

        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(email));

        verify(emailSender).sendVerificationCode(eq(email), anyString());
        verify(emailVerificationRepository).saveAndFlush(any(EmailVerification.class));
    }

    @Test
    @DisplayName("예외 메시지에서 이메일 unique 충돌을 확인하면 EMAIL_RESEND_TOO_SOON을 던진다")
    void sendEmailVerificationCode_messageFallbackUniqueConflict_throws() {
        String email = "race@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.saveAndFlush(any(EmailVerification.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate email",
                                new IllegalStateException(
                                        "Duplicate entry 'race@example.com' for key "
                                                + "'email_verification.uk_email_verification_email'")));

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_RESEND_TOO_SOON));
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("Hibernate 제약조건 이름으로 이메일 unique 충돌을 확인한다")
    void sendEmailVerificationCode_structuredConstraintNameConflict_throws() {
        String email = "structured-race@example.com";
        SQLException sqlException = new SQLException("duplicate", "23000", 1062);
        ConstraintViolationException constraintException =
                new ConstraintViolationException(
                        "could not execute statement",
                        sqlException,
                        "insert into email_verification",
                        "`email_verification`.`UK_EMAIL_VERIFICATION_EMAIL`");
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.saveAndFlush(any(EmailVerification.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate email", constraintException));

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_RESEND_TOO_SOON));
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("제약조건 이름이 없으면 MySQL 1062 오류 코드로 unique 충돌을 확인한다")
    void sendEmailVerificationCode_mysqlDuplicateErrorCodeConflict_throws() {
        String email = "mysql-code-race@example.com";
        SQLException sqlException = new SQLException("duplicate", "23000", 1062);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.saveAndFlush(any(EmailVerification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email", sqlException));

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_RESEND_TOO_SOON));
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("구조화된 제약조건 이름이 다른 unique 충돌이면 원본 예외를 유지한다")
    void sendEmailVerificationCode_differentStructuredConstraint_rethrowsOriginalException() {
        String email = "different-constraint@example.com";
        SQLException sqlException = new SQLException("duplicate", "23000", 1062);
        ConstraintViolationException constraintException =
                new ConstraintViolationException(
                        "could not execute statement",
                        sqlException,
                        "insert into email_verification",
                        "uk_different_constraint");
        DataIntegrityViolationException integrityException =
                new DataIntegrityViolationException("duplicate value", constraintException);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.saveAndFlush(any(EmailVerification.class)))
                .thenThrow(integrityException);

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isSameAs(integrityException);
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("최초 발송 저장의 이메일 unique 충돌이 아닌 무결성 오류는 원본 예외를 유지한다")
    void sendEmailVerificationCode_nonUniqueIntegrityViolation_rethrowsOriginalException() {
        String email = "invalid-schema@example.com";
        DataIntegrityViolationException integrityException =
                new DataIntegrityViolationException("Column 'code' cannot be null");
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.saveAndFlush(any(EmailVerification.class)))
                .thenThrow(integrityException);

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isSameAs(integrityException);
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("재발송 실패 보상 중 DB 오류가 발생해도 SMTP 실패를 원인으로 유지한다")
    void sendEmailVerificationCode_compensationFailure_preservesSmtpFailure() {
        String email = "compensation-failure@example.com";
        RuntimeException smtpException = new RuntimeException("smtp down");
        EmailVerification existing =
                EmailVerification.create(email, "111111", LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(existing, "id", 1L);
        ReflectionTestUtils.setField(existing, "version", 0L);
        ReflectionTestUtils.setField(existing, "createdAt", LocalDateTime.now().minusMinutes(5));
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.existsByEmail(email)).thenReturn(true);
        when(emailVerificationRepository.findByEmailForUpdate(email))
                .thenReturn(Optional.of(existing));
        when(emailVerificationRepository.saveAndFlush(existing)).thenReturn(existing);
        when(emailVerificationRepository.restoreAfterFailedResend(
                        any(),
                        any(),
                        anyString(),
                        anyBoolean(),
                        any(),
                        any(),
                        any(),
                        anyInt(),
                        anyInt()))
                .thenThrow(new DataIntegrityViolationException("compensation failed"));
        doThrow(smtpException).when(emailSender).sendVerificationCode(anyString(), anyString());

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
                            assertThat(exception.getCause()).isSameAs(smtpException);
                        });
    }

    @Test
    @DisplayName("재발송 쿨다운 이내면 발송하지 않고 EMAIL_RESEND_TOO_SOON을 던진다")
    void sendEmailVerificationCode_withinCooldown_throws() {
        String email = "cooldown@example.com";
        EmailVerification existing =
                EmailVerification.create(email, "111111", LocalDateTime.now().plusMinutes(10));
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.existsByEmail(email)).thenReturn(true);
        when(emailVerificationRepository.findByEmailForUpdate(email))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_RESEND_TOO_SOON));
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("당일 발송 한도에 도달하면 EMAIL_SEND_LIMIT_EXCEEDED를 던진다")
    void sendEmailVerificationCode_dailyLimitReached_throws() {
        String email = "limit@example.com";
        EmailVerification existing =
                EmailVerification.create(email, "111111", LocalDateTime.now().plusMinutes(10));
        // 쿨다운은 지났지만 같은 날 발송 한도(5회)를 채운 상태를 재현한다.
        ReflectionTestUtils.setField(existing, "createdAt", LocalDateTime.now().minusMinutes(5));
        ReflectionTestUtils.setField(existing, "dailySendCount", 5);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(emailVerificationRepository.existsByEmail(email)).thenReturn(true);
        when(emailVerificationRepository.findByEmailForUpdate(email))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(email)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT_EXCEEDED));
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("틀린 코드를 입력하면 시도 횟수가 증가하고 INVALID_VERIFICATION_CODE를 던진다")
    void confirmEmailVerificationCode_wrongCode_increasesAttempt() {
        String email = "wrong@example.com";
        EmailVerification existing =
                EmailVerification.create(email, "123456", LocalDateTime.now().plusMinutes(10));
        when(emailVerificationRepository.existsByEmail(email)).thenReturn(true);
        when(emailVerificationRepository.findByEmailForUpdate(email))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(email, "000000")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));
        assertThat(existing.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시도 횟수를 모두 소진하면 올바른 코드라도 EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED를 던진다")
    void confirmEmailVerificationCode_attemptsExceeded_throws() {
        String email = "exceeded@example.com";
        EmailVerification existing =
                EmailVerification.create(email, "123456", LocalDateTime.now().plusMinutes(10));
        for (int i = 0; i < 5; i++) {
            existing.increaseAttempt();
        }
        when(emailVerificationRepository.existsByEmail(email)).thenReturn(true);
        when(emailVerificationRepository.findByEmailForUpdate(email))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(email, "123456")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED));
    }

    @Test
    @DisplayName("발송된 적 없는 이메일이면 잠금 조회 없이 EMAIL_VERIFICATION_NOT_FOUND를 던진다")
    void confirmEmailVerificationCode_unknownEmail_doesNotTakeLock() {
        String email = "unknown@example.com";
        when(emailVerificationRepository.existsByEmail(email)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(email, "123456")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
        // 없는 행에 FOR UPDATE를 걸면 빈 갭에 gap lock이 잡히므로 잠금 조회 자체가 일어나면 안 된다.
        verify(emailVerificationRepository, never()).findByEmailForUpdate(any());
    }

    @Test
    @DisplayName("회원가입은 level=2 시군구 활동 지역 1개를 User에 저장한다")
    void signup_withLevel2ActivityRegion_savesUserActivityRegion() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        prepareVerifiedEmail();
        when(regionRepository.findById(123L)).thenReturn(Optional.of(activityRegion));
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(signupRequest(123L));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getActivityRegion()).isSameAs(activityRegion);
        assertThat(captor.getValue().getInterestCategories())
                .containsExactly(PostingCategory.WELFARE);
    }

    @Test
    @DisplayName("회원가입에서 관심 카테고리가 null이면 실패한다")
    void signup_withNullInterestCategories_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(null);
    }

    @Test
    @DisplayName("회원가입에서 null 관심 카테고리가 포함되면 실패한다")
    void signup_withNullInterestCategory_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(Collections.singletonList(null));
    }

    @Test
    @DisplayName("회원가입에서 관심 카테고리가 비어 있으면 실패한다")
    void signup_withEmptyInterestCategories_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(List.of());
    }

    @Test
    @DisplayName("회원가입에서 관심 카테고리가 중복되면 실패한다")
    void signup_withDuplicateInterestCategories_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(List.of(PostingCategory.WELFARE, PostingCategory.WELFARE));
    }

    @Test
    @DisplayName("회원가입은 서로 다른 관심 카테고리 여러 개를 모두 User에 저장한다")
    void signup_withMultipleInterestCategories_savesAllInterestCategories() {
        prepareVerifiedEmail();
        when(regionRepository.findById(123L))
                .thenReturn(Optional.of(Region.create("강남구", 2, "11680", null)));
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(
                signupRequest(
                        123L,
                        "홍길동",
                        "길동",
                        List.of(
                                PostingCategory.WELFARE,
                                PostingCategory.EDUCATION,
                                PostingCategory.OVERSEAS)));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getInterestCategories())
                .containsExactly(
                        PostingCategory.WELFARE,
                        PostingCategory.EDUCATION,
                        PostingCategory.OVERSEAS);
    }

    @Test
    @DisplayName("회원가입에서 level=1 시도를 활동 지역으로 선택하면 실패한다")
    void signup_withLevel1ActivityRegion_throwsRegionNotFound() {
        prepareVerifiedEmail();
        when(regionRepository.findById(1L))
                .thenReturn(Optional.of(Region.create("서울", 1, "11", null)));

        assertThatThrownBy(() -> authService.signup(signupRequest(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REGION_NOT_FOUND));
    }

    @Test
    @DisplayName("회원가입에서 존재하지 않는 활동 지역 ID를 선택하면 실패한다")
    void signup_withUnknownActivityRegion_throwsRegionNotFound() {
        prepareVerifiedEmail();
        when(regionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signup(signupRequest(999L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REGION_NOT_FOUND));
    }

    @Test
    @DisplayName("회원가입에서 활동 지역 ID가 null이면 실패한다")
    void signup_withNullActivityRegion_throwsInvalidActivityRegion() {
        assertThatThrownBy(() -> authService.signup(signupRequest(null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_ACTIVITY_REGION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나", "가나다라마바사아자차", "Ab", "abcdefghijklmnopqrst"})
    @DisplayName("회원가입은 정책에 맞는 이름을 허용한다")
    void signup_withValidName_savesName(String name) {
        prepareSuccessfulSignup();

        authService.signup(signupRequest(123L, name, "길동"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(name);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "가",
                "가나다라마바사아자차카",
                "A",
                "abcdefghijklmnopqrstu",
                "홍John",
                "John1",
                "John!",
                " John",
                "John Smith",
                "ㅎㄱ"
            })
    @DisplayName("회원가입은 정책에 맞지 않는 이름을 거부한다")
    void signup_withInvalidName_throwsValidationError(String name) {
        assertValidationError(signupRequest(123L, name, "길동"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나", "가나다라마바사아자차", "Ab", "abcdefghijklmnopqrst"})
    @DisplayName("회원가입은 정책에 맞는 닉네임을 허용한다")
    void signup_withValidNickname_savesNickname(String nickname) {
        prepareSuccessfulSignup();

        authService.signup(signupRequest(123L, "홍길동", nickname));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo(nickname);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "가",
                "가나다라마바사아자차카",
                "A",
                "abcdefghijklmnopqrstu",
                "홍John",
                "John1",
                "John!",
                " John",
                "John Smith",
                "ㅎㄱ"
            })
    @DisplayName("회원가입은 정책에 맞지 않는 닉네임을 거부한다")
    void signup_withInvalidNickname_throwsValidationError(String nickname) {
        assertValidationError(signupRequest(123L, "홍길동", nickname));
    }

    @Test
    @DisplayName("재가입 제한 중인 전화번호는 가용하지 않다고 응답한다")
    void checkPhoneNumberAvailability_whenRejoinBlocked_returnsUnavailable() {
        when(accountRejoinBlockService.isPhoneBlocked(eq("01012345678"), any(LocalDateTime.class)))
                .thenReturn(true);

        var response =
                authService.checkPhoneNumberAvailability(
                        new PhoneNumberAvailabilityRequest("010-1234-5678"));

        assertThat(response.available()).isFalse();
        verify(userRepository, never()).existsByPhoneNumber(anyString());
    }

    @Test
    @DisplayName("재가입 제한 중인 전화번호는 회원가입을 거부한다")
    void signup_whenPhoneRejoinBlocked_throwsAccountRejoinBlocked() {
        prepareVerifiedEmail();
        when(accountRejoinBlockService.isPhoneBlocked(eq("01012345678"), any(LocalDateTime.class)))
                .thenReturn(true);

        assertErrorCode(
                () -> authService.signup(signupRequest(123L)), ErrorCode.ACCOUNT_REJOIN_BLOCKED);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("login은 이메일과 비밀번호가 일치하는 활성 회원에게 새 토큰을 발급한다")
    void login_withValidCredentials_issuesTokens() {
        User user = activeUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded-password")).thenReturn(true);
        when(lockedTokenIssuanceService.issue(user.getId()))
                .thenReturn(new TokenIssueResult("new-access-token", "new-refresh-token"));

        TokenIssueResult result = authService.login(loginRequest());

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        verify(lockedTokenIssuanceService).issue(user.getId());
    }

    @Test
    @DisplayName("login은 존재하지 않는 이메일이면 INVALID_LOGIN이고 토큰을 발급하지 않는다")
    void login_withUnknownEmail_throwsInvalidLogin() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertErrorCode(() -> authService.login(loginRequest()), ErrorCode.INVALID_LOGIN);

        verify(tokenProvider, never()).createAccessToken(any(User.class));
    }

    @Test
    @DisplayName("login은 비밀번호가 일치하지 않으면 INVALID_LOGIN이고 토큰을 발급하지 않는다")
    void login_withWrongPassword_throwsInvalidLogin() {
        User user = activeUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded-password")).thenReturn(false);

        assertErrorCode(() -> authService.login(loginRequest()), ErrorCode.INVALID_LOGIN);

        verify(tokenProvider, never()).createAccessToken(any(User.class));
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"SUSPENDED", "WITHDRAWAL_PENDING", "WITHDRAWN"})
    @DisplayName("login은 정지·탈퇴 처리 중·탈퇴 회원의 토큰 발급을 차단한다")
    void login_withBlockedUserStatus_throwsStatusError(UserStatus status) {
        User user = mock(User.class);
        when(user.getPassword()).thenReturn("encoded-password");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded-password")).thenReturn(true);
        when(lockedTokenIssuanceService.issue(user.getId()))
                .thenThrow(new BusinessException(errorCodeFor(status)));

        assertErrorCode(() -> authService.login(loginRequest()), errorCodeFor(status));

        verify(tokenProvider, never()).createAccessToken(any(User.class));
    }

    @Test
    @DisplayName("reissue는 기존 Refresh Token을 revoke하고 새 토큰을 저장한다")
    void reissue_revokesOldRefreshTokenAndStoresNewRefreshToken() {
        User user = activeUser();
        RefreshToken oldRefreshToken =
                RefreshToken.create("old-refresh-hash", user, LocalDateTime.now().plusDays(1));
        when(tokenProvider.hashToken("old-refresh-token")).thenReturn("old-refresh-hash");
        when(refreshTokenRepository.findUserIdByTokenHash("old-refresh-hash"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate("old-refresh-hash"))
                .thenReturn(Optional.of(oldRefreshToken));
        when(tokenProvider.createAccessToken(user)).thenReturn("new-access-token");
        when(tokenProvider.generateToken()).thenReturn("new-refresh-token");
        when(tokenProvider.hashToken("new-refresh-token")).thenReturn("new-refresh-hash");
        when(tokenProvider.refreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(14));

        TokenIssueResult result = authService.reissue("old-refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(oldRefreshToken.isRevoked()).isTrue();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("new-refresh-hash");
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("이미 revoke된 Refresh Token으로 reissue하면 REVOKED_TOKEN이고 새 토큰을 저장하지 않는다")
    void reissue_withRevokedRefreshToken_throwsRevokedToken() {
        RefreshToken revokedRefreshToken =
                RefreshToken.create(
                        "old-refresh-hash", activeUser(), LocalDateTime.now().plusDays(1));
        revokedRefreshToken.revoke(LocalDateTime.now());
        when(tokenProvider.hashToken("old-refresh-token")).thenReturn("old-refresh-hash");
        when(refreshTokenRepository.findUserIdByTokenHash("old-refresh-hash"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(revokedRefreshToken.getUser()));
        when(refreshTokenRepository.findByTokenHashForUpdate("old-refresh-hash"))
                .thenReturn(Optional.of(revokedRefreshToken));

        assertThatThrownBy(() -> authService.reissue("old-refresh-token"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REVOKED_TOKEN));

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"SUSPENDED", "WITHDRAWAL_PENDING", "WITHDRAWN"})
    @DisplayName("reissue는 정지·탈퇴 처리 중·탈퇴 회원의 토큰 재발급을 차단한다")
    void reissue_withBlockedUserStatus_throwsStatusError(UserStatus status) {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(status);
        RefreshToken refreshToken =
                RefreshToken.create("old-refresh-hash", user, LocalDateTime.now().plusDays(1));
        when(tokenProvider.hashToken("old-refresh-token")).thenReturn("old-refresh-hash");
        when(refreshTokenRepository.findUserIdByTokenHash("old-refresh-hash"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate("old-refresh-hash"))
                .thenReturn(Optional.of(refreshToken));

        assertErrorCode(() -> authService.reissue("old-refresh-token"), errorCodeFor(status));

        assertThat(refreshToken.isRevoked()).isFalse();
        verify(tokenProvider, never()).createAccessToken(any(User.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("logout은 유효한 Refresh Token을 revoke한다")
    void logout_revokesRefreshToken() {
        RefreshToken refreshToken =
                RefreshToken.create("refresh-hash", activeUser(), LocalDateTime.now().plusDays(1));
        when(tokenProvider.hashToken("refresh-token")).thenReturn("refresh-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("refresh-hash"))
                .thenReturn(Optional.of(refreshToken));

        authService.logout("refresh-token");

        assertThat(refreshToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 Refresh Token으로 logout하면 INVALID_TOKEN이다")
    void logout_withUnknownRefreshToken_throwsInvalidToken() {
        when(tokenProvider.hashToken("unknown-refresh-token")).thenReturn("unknown-refresh-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("unknown-refresh-hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("unknown-refresh-token"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    private static User activeUser() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        return User.create(
                "홍길동",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                "01012345678",
                "test@example.com",
                "encoded-password",
                "길동",
                null,
                true,
                true,
                false,
                activityRegion,
                List.of());
    }

    private void prepareTokenIssue(User user) {
        when(tokenProvider.createAccessToken(user)).thenReturn("new-access-token");
        when(tokenProvider.generateToken()).thenReturn("new-refresh-token");
        when(tokenProvider.hashToken("new-refresh-token")).thenReturn("new-refresh-hash");
        when(tokenProvider.refreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(14));
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private static ErrorCode errorCodeFor(UserStatus status) {
        return switch (status) {
            case SUSPENDED -> ErrorCode.SUSPENDED_USER;
            case WITHDRAWAL_PENDING -> ErrorCode.WITHDRAWAL_PENDING_USER;
            case WITHDRAWN -> ErrorCode.WITHDRAWN_USER;
            case ACTIVE -> throw new IllegalArgumentException("활성 사용자는 차단 상태가 아닙니다.");
        };
    }

    private void prepareVerifiedEmail() {
        EmailVerification emailVerification =
                EmailVerification.create(
                        "test@example.com", "123456", LocalDateTime.now().plusMinutes(10));
        emailVerification.verify(LocalDateTime.now());
        when(emailVerificationRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(emailVerification));
    }

    private void prepareSuccessfulSignup() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        prepareVerifiedEmail();
        when(regionRepository.findById(123L)).thenReturn(Optional.of(activityRegion));
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertValidationError(SignupRequest request) {
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private void assertInvalidInterestCategories(List<PostingCategory> interestCategories) {
        assertThatThrownBy(
                        () ->
                                authService.signup(
                                        signupRequest(123L, "홍길동", "길동", interestCategories)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INTEREST_CATEGORY_COUNT));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private static SignupRequest signupRequest(Long activityRegionId) {
        return signupRequest(activityRegionId, "홍길동", "길동");
    }

    private static LoginRequest loginRequest() {
        return new LoginRequest("test@example.com", "password123!");
    }

    private static SignupRequest signupRequest(
            Long activityRegionId, String name, String nickname) {
        return signupRequest(activityRegionId, name, nickname, List.of(PostingCategory.WELFARE));
    }

    private static SignupRequest signupRequest(
            Long activityRegionId,
            String name,
            String nickname,
            List<PostingCategory> interestCategories) {
        return new SignupRequest(
                name,
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                "01012345678",
                "test@example.com",
                "password123!",
                "password123!",
                nickname,
                null,
                activityRegionId,
                interestCategories,
                true,
                true,
                false);
    }
}
