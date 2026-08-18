package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.dto.PasswordChangeRequest;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CURRENT_PASSWORD = "oldpass123";
    private static final String NEW_PASSWORD = "newpass123";
    private static final String ENCODED_CURRENT_PASSWORD = "encoded-old";
    private static final String ENCODED_NEW_PASSWORD = "encoded-new";

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AccountLoginTypeResolver accountLoginTypeResolver;
    @Mock private PasswordEncoder passwordEncoder;

    // 상태 제재는 실제 정책으로 검증하면서 호출 여부·순서까지 확인하기 위해 spy를 쓴다.
    private final LoginPolicy loginPolicy = spy(new LoginPolicy());

    private PasswordChangeService passwordChangeService;

    @BeforeEach
    void setUp() {
        passwordChangeService =
                new PasswordChangeService(
                        userRepository,
                        passwordResetTokenRepository,
                        refreshTokenRepository,
                        accountLoginTypeResolver,
                        loginPolicy,
                        new PasswordPolicy(),
                        passwordEncoder);
    }

    @Test
    @DisplayName("이메일 계정은 비밀번호를 바꾸고 재설정 토큰과 Refresh Token을 모두 폐기한다")
    void changePassword_succeedsForEmailAccount() {
        User user = emailUser();
        lockedUser(user);
        resolvesCredentialType(AccountLoginType.EMAIL);
        when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_CURRENT_PASSWORD)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_NEW_PASSWORD);

        passwordChangeService.changePassword(USER_ID, request(CURRENT_PASSWORD, NEW_PASSWORD));

        assertThat(user.getPassword()).isEqualTo(ENCODED_NEW_PASSWORD);
        verify(passwordResetTokenRepository).deleteAllByUserId(USER_ID);
        verify(refreshTokenRepository).deleteAllByUserId(USER_ID);
        // 상태 검증은 잠금으로 읽은 최신 User를 기준으로 해야 한다.
        InOrder inOrder = inOrder(userRepository, loginPolicy);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(loginPolicy).validateLoginAllowed(user);
    }

    @Test
    @DisplayName("현재 비밀번호가 다르면 CURRENT_PASSWORD_MISMATCH로 실패한다")
    void changePassword_throwsCurrentPasswordMismatch() {
        User user = emailUser();
        lockedUser(user);
        resolvesCredentialType(AccountLoginType.EMAIL);
        when(passwordEncoder.matches("wrongpass", ENCODED_CURRENT_PASSWORD)).thenReturn(false);

        assertChangeFails(
                request("wrongpass", NEW_PASSWORD), ErrorCode.CURRENT_PASSWORD_MISMATCH, user);
    }

    @Test
    @DisplayName("확인값이 다르면 PASSWORD_MISMATCH로 실패하고 User를 잠그지 않는다")
    void changePassword_throwsPasswordMismatch_withoutLockingUser() {
        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        USER_ID,
                                        new PasswordChangeRequest(
                                                CURRENT_PASSWORD, NEW_PASSWORD, "otherpass1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_MISMATCH);

        // 명백히 잘못된 요청이 User 잠금을 잡지 않아야 한다.
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("정책을 위반한 새 비밀번호는 VALIDATION_ERROR로 실패하고 User를 잠그지 않는다")
    void changePassword_throwsValidationError_withoutLockingUser() {
        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        USER_ID, request(CURRENT_PASSWORD, "short")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("카카오 전용 계정은 PASSWORD_CHANGE_NOT_AVAILABLE로 거부하고 비밀번호를 만들지 않는다")
    void changePassword_throwsNotAvailableForKakaoOnlyAccount() {
        User user = kakaoOnlyUser();
        lockedUser(user);
        resolvesCredentialType(AccountLoginType.KAKAO);

        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        USER_ID, request(CURRENT_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_CHANGE_NOT_AVAILABLE);

        assertThat(user.getPassword()).isNull();
        // 카카오 계정에서는 현재 비밀번호 확인 자체를 시도하지 않는다.
        verifyNoInteractions(passwordEncoder);
        verifyNoTokenCleanup();
    }

    @Test
    @DisplayName("credential 구조가 깨진 계정은 INTERNAL_SERVER_ERROR로 실패한다")
    void changePassword_throwsInternalServerError_whenCredentialInvariantViolated() {
        User user = emailUser();
        lockedUser(user);
        when(accountLoginTypeResolver.resolveCredentialTypeIgnoringStatus(user))
                .thenReturn(Optional.empty());

        assertChangeFails(
                request(CURRENT_PASSWORD, NEW_PASSWORD), ErrorCode.INTERNAL_SERVER_ERROR, user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("정지된 계정은 SUSPENDED_USER로 거부한다")
    void changePassword_throwsSuspendedUser() {
        User user = emailUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        lockedUser(user);

        assertChangeFails(request(CURRENT_PASSWORD, NEW_PASSWORD), ErrorCode.SUSPENDED_USER, user);
        verifyNoInteractions(accountLoginTypeResolver);
    }

    @Test
    @DisplayName("탈퇴 처리 중인 계정은 WITHDRAWAL_PENDING_USER로 거부한다")
    void changePassword_throwsWithdrawalPendingUser() {
        User user = emailUser();
        user.requestWithdrawal(WithdrawalReason.SELF, LocalDateTime.of(2026, 8, 18, 12, 0));
        lockedUser(user);

        assertChangeFails(
                request(CURRENT_PASSWORD, NEW_PASSWORD), ErrorCode.WITHDRAWAL_PENDING_USER, user);
        verifyNoInteractions(accountLoginTypeResolver);
    }

    @Test
    @DisplayName("탈퇴한 계정은 WITHDRAWN_USER로 거부한다")
    void changePassword_throwsWithdrawnUser() {
        User user = emailUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);
        lockedUser(user);

        assertChangeFails(request(CURRENT_PASSWORD, NEW_PASSWORD), ErrorCode.WITHDRAWN_USER, user);
        verifyNoInteractions(accountLoginTypeResolver);
    }

    @Test
    @DisplayName("사용자가 없으면 USER_NOT_FOUND로 실패한다")
    void changePassword_throwsUserNotFound() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        USER_ID, request(CURRENT_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verifyNoTokenCleanup();
    }

    private void assertChangeFails(PasswordChangeRequest request, ErrorCode errorCode, User user) {
        String passwordBefore = user.getPassword();

        assertThatThrownBy(() -> passwordChangeService.changePassword(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);

        assertThat(user.getPassword()).isEqualTo(passwordBefore);
        verifyNoTokenCleanup();
    }

    private void verifyNoTokenCleanup() {
        verify(passwordResetTokenRepository, never()).deleteAllByUserId(any());
        verify(refreshTokenRepository, never()).deleteAllByUserId(any());
    }

    private void lockedUser(User user) {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    }

    private void resolvesCredentialType(AccountLoginType loginType) {
        when(accountLoginTypeResolver.resolveCredentialTypeIgnoringStatus(any()))
                .thenReturn(Optional.of(loginType));
    }

    private PasswordChangeRequest request(String currentPassword, String password) {
        return new PasswordChangeRequest(currentPassword, password, password);
    }

    private User emailUser() {
        User user =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        ENCODED_CURRENT_PASSWORD,
                        "길동",
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private User kakaoOnlyUser() {
        User user =
                User.createSocial(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "길동",
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
