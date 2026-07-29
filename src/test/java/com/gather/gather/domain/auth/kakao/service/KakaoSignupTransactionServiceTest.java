package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.SocialAccountConstraintResolver;
import com.gather.gather.domain.auth.service.SocialAccountIdentityService;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KakaoSignupTransactionServiceTest {

    private static final String PROVIDER_USER_ID = "123456789";
    private static final String PROVIDER_USER_KEY = "a".repeat(64);
    private static final String SIGNUP_TOKEN = "A".repeat(43);
    private static final String TOKEN_HASH = "b".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final EncryptedProviderUserId ENCRYPTED_PROVIDER_USER_ID =
            new EncryptedProviderUserId("ciphertext", 4);

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private SocialSignupSessionRepository signupSessionRepository;
    @Mock private SocialSignupTokenService signupTokenService;
    @Mock private SocialAccountIdentityService socialAccountIdentityService;
    @Mock private SignupValidator signupValidator;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private SocialAccountProviderIdCipher providerIdCipher;

    private KakaoSignupTransactionService service;
    private SocialSignupSession session;

    @BeforeEach
    void setUp() {
        service =
                new KakaoSignupTransactionService(
                        userRepository,
                        socialAccountRepository,
                        signupSessionRepository,
                        signupTokenService,
                        socialAccountIdentityService,
                        signupValidator,
                        tokenIssuer,
                        providerIdCipher,
                        new SocialAccountConstraintResolver(),
                        CLOCK);
        session =
                SocialSignupSession.create(
                        TOKEN_HASH,
                        SocialProvider.KAKAO,
                        PROVIDER_USER_KEY,
                        3,
                        ENCRYPTED_PROVIDER_USER_ID,
                        NOW.plusMinutes(15),
                        NOW);
        stubPendingSession();
    }

    @Test
    @DisplayName("가입 트랜잭션은 세션 identity로 SocialAccount를 만들고 세션을 소비한다")
    void createAccount_savesLifecycleIdentityAndConsumesSession() {
        User user = socialUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        when(tokenIssuer.issue(user))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        TokenIssueResult result = service.createAccount(user, SIGNUP_TOKEN, "01012345678", "길동");

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).saveAndFlush(captor.capture());
        SocialAccount account = captor.getValue();
        assertThat(account.getProviderUserKey()).isEqualTo(PROVIDER_USER_KEY);
        assertThat(account.getProviderUserKeyVersion()).isEqualTo(3);
        assertThat(account.getProviderUserIdCiphertext()).isEqualTo("ciphertext");
        assertThat(account.getEncryptionKeyVersion()).isEqualTo(4);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(account.getGeneration()).isEqualTo(1L);
        assertThat(ReflectionTestUtils.getField(account, "legacyProviderUserId"))
                .isEqualTo(PROVIDER_USER_ID);
        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.CONSUMED);
        assertThat(session.getConsumedAt()).isEqualTo(NOW);
        assertThat(session.getCancelledAt()).isNull();
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("가입 성공 시 같은 identity의 다른 PENDING 세션을 취소한다")
    void createAccount_cancelsOtherPendingSessions() {
        SocialSignupSession other =
                SocialSignupSession.create(
                        "c".repeat(64),
                        SocialProvider.KAKAO,
                        PROVIDER_USER_KEY,
                        3,
                        ENCRYPTED_PROVIDER_USER_ID,
                        NOW.plusMinutes(15),
                        NOW);
        when(signupSessionRepository.findAllByIdentityAndStatusForUpdate(
                        SocialProvider.KAKAO, PROVIDER_USER_KEY, SocialSignupSessionStatus.PENDING))
                .thenReturn(List.of(session, other));
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        when(tokenIssuer.issue(user))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        service.createAccount(user, SIGNUP_TOKEN, "01012345678", "길동");

        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.CONSUMED);
        assertThat(session.getConsumedAt()).isEqualTo(NOW);
        assertThat(other.getStatus()).isEqualTo(SocialSignupSessionStatus.CANCELLED);
        assertThat(other.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("LINKED SocialAccount가 최신 조회되면 User 생성 전에 가입을 거부한다")
    void createAccount_linkedAccount_throwsAlreadyRegistered() {
        when(socialAccountIdentityService.findByProviderAndKey(any(), any()))
                .thenReturn(Optional.of(linkedSocialAccount()));

        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("만료·소비·취소 세션은 가입에 사용할 수 없다")
    void createAccount_invalidSessionState_rejects() {
        session.consume(NOW.plusMinutes(1));

        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("만료된 PENDING 세션은 SIGNUP_TOKEN_EXPIRED로 거부한다")
    void createAccount_expiredSession_rejects() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 29, 12, 0);
        SocialSignupSession expired =
                SocialSignupSession.create(
                        TOKEN_HASH,
                        SocialProvider.KAKAO,
                        PROVIDER_USER_KEY,
                        3,
                        ENCRYPTED_PROVIDER_USER_ID,
                        createdAt.plusMinutes(15),
                        createdAt);
        when(signupSessionRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expired));
        when(signupSessionRepository.findAllByIdentityAndStatusForUpdate(
                        SocialProvider.KAKAO, PROVIDER_USER_KEY, SocialSignupSessionStatus.PENDING))
                .thenReturn(List.of(expired));

        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.SIGNUP_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("CANCELLED 세션은 SIGNUP_TOKEN_INVALID로 거부한다")
    void createAccount_cancelledSession_rejects() {
        session.cancel(NOW.plusMinutes(1));

        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("UNLINK_PENDING와 UNLINKED SocialAccount는 재가입시키지 않는다")
    void createAccount_unlinkedAccount_rejectsRelink() {
        SocialAccount account = linkedSocialAccount();
        account.markUnlinkPending(LocalDateTime.now());
        when(socialAccountIdentityService.findByProviderAndKey(any(), any()))
                .thenReturn(Optional.of(account));

        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);

        account.markUnlinked(LocalDateTime.now());
        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
    }

    @Test
    @DisplayName("provider key UNIQUE 충돌만 rollback용 전용 예외로 분류한다")
    void createAccount_providerKeyConflict_throwsClassifiedException() {
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_provider_key'");
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class))).thenThrow(exception);

        assertThatThrownBy(() -> service.createAccount(user, SIGNUP_TOKEN, "01012345678", "길동"))
                .isInstanceOf(SocialAccountProviderKeyConflictException.class)
                .hasCause(exception);
        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
    }

    @Test
    @DisplayName("dual-write legacy provider ID UNIQUE 충돌도 동일 계정 경쟁으로 분류한다")
    void createAccount_legacyProviderIdConflict_throwsClassifiedException() {
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_provider_user'");
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class))).thenThrow(exception);

        assertThatThrownBy(() -> service.createAccount(user, SIGNUP_TOKEN, "01012345678", "길동"))
                .isInstanceOf(SocialAccountProviderKeyConflictException.class)
                .hasCause(exception);
    }

    private void stubPendingSession() {
        lenient().when(signupTokenService.hashToken(SIGNUP_TOKEN)).thenReturn(TOKEN_HASH);
        lenient()
                .when(signupSessionRepository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(session));
        lenient()
                .when(
                        signupSessionRepository.findAllByIdentityAndStatusForUpdate(
                                SocialProvider.KAKAO,
                                PROVIDER_USER_KEY,
                                SocialSignupSessionStatus.PENDING))
                .thenReturn(List.of(session));
        lenient()
                .when(socialAccountIdentityService.findByProviderAndKey(any(), any()))
                .thenReturn(Optional.empty());
    }

    private SocialAccount linkedSocialAccount() {
        return SocialAccount.createLinked(
                socialUser(),
                SocialProvider.KAKAO,
                PROVIDER_USER_ID,
                PROVIDER_USER_KEY,
                3,
                ENCRYPTED_PROVIDER_USER_ID,
                LocalDateTime.now());
    }

    private User socialUser() {
        return User.createSocial(
                "홍길동",
                LocalDate.of(2002, 3, 15),
                Gender.MALE,
                "01012345678",
                "길동",
                null,
                true,
                true,
                false,
                null,
                List.of(PostingCategory.WELFARE));
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
