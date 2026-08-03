package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.AccountIdentityGuardService;
import com.gather.gather.domain.auth.service.AccountRejoinBlockService;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
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

@ExtendWith(MockitoExtension.class)
class KakaoSignupTransactionServiceTest {

    private static final String PROVIDER_USER_ID = "123456789";
    private static final String PROVIDER_USER_KEY = "a".repeat(64);
    private static final String SIGNUP_TOKEN = "A".repeat(43);
    private static final String TOKEN_HASH = "b".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final RejoinBlockIdentifier IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, PROVIDER_USER_KEY, 3);
    private static final EncryptedProviderUserId ENCRYPTED_PROVIDER_USER_ID =
            new EncryptedProviderUserId("ciphertext", 4);
    private static final SocialSignupIdentitySnapshot IDENTITY =
            new SocialSignupIdentitySnapshot(
                    SocialProvider.KAKAO, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private SocialSignupSessionService signupSessionService;
    @Mock private SocialAccountIdentityService socialAccountIdentityService;
    @Mock private SignupValidator signupValidator;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private SocialAccountProviderIdCipher providerIdCipher;
    @Mock private AccountRejoinBlockService accountRejoinBlockService;
    @Mock private AccountIdentityGuardService accountIdentityGuardService;

    private KakaoSignupTransactionService service;
    private SocialSignupSession target;
    private SocialSignupSession sibling;

    @BeforeEach
    void setUp() {
        service =
                new KakaoSignupTransactionService(
                        userRepository,
                        socialAccountRepository,
                        signupSessionService,
                        socialAccountIdentityService,
                        signupValidator,
                        tokenIssuer,
                        providerIdCipher,
                        new SocialAccountConstraintResolver(),
                        accountRejoinBlockService,
                        accountIdentityGuardService,
                        CLOCK);
        target = session(TOKEN_HASH);
        sibling = session("c".repeat(64));
        LockedSocialSignupSession locked =
                new LockedSocialSignupSession(target, List.of(target, sibling), IDENTITY);
        lenient().when(signupSessionService.lockForSignup(SIGNUP_TOKEN, NOW)).thenReturn(locked);
        lenient()
                .when(socialAccountIdentityService.findByProviderAndKey(any(), any()))
                .thenReturn(Optional.empty());
        lenient()
                .when(accountIdentityGuardService.lockPhone("01012345678", NOW))
                .thenReturn(
                        new RejoinBlockIdentifier(
                                AccountRejoinBlockIdentifierType.PHONE, "c".repeat(64), 3));
    }

    @Test
    @DisplayName("가입 트랜잭션은 snapshot identity로 SocialAccount를 만들고 세션 전이를 완료한다")
    void createAccount_savesIdentityAndTransitionsSessions() {
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        when(tokenIssuer.issue(user))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        TokenIssueResult result = service.createAccount(user, SIGNUP_TOKEN, "01012345678", "길동");

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).saveAndFlush(captor.capture());
        SocialAccount account = captor.getValue();
        assertThat(account.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(account.getProviderUserKey()).isEqualTo(PROVIDER_USER_KEY);
        assertThat(account.getProviderUserKeyVersion()).isEqualTo(3);
        assertThat(account.getProviderUserIdCiphertext()).isEqualTo("ciphertext");
        assertThat(account.getEncryptionKeyVersion()).isEqualTo(4);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(account.getGeneration()).isEqualTo(1L);
        verify(providerIdCipher).decrypt(ENCRYPTED_PROVIDER_USER_ID);
        assertThat(target.getStatus()).isEqualTo(SocialSignupSessionStatus.CONSUMED);
        assertThat(target.getConsumedAt()).isEqualTo(NOW);
        assertThat(sibling.getStatus()).isEqualTo(SocialSignupSessionStatus.CANCELLED);
        assertThat(sibling.getCancelledAt()).isEqualTo(NOW);
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("세션 최종 검증 실패는 모든 저장과 token 발급 전에 차단한다")
    void createAccount_invalidSession_doesNotPersistAnything() {
        BusinessException invalid = new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        when(signupSessionService.lockForSignup(SIGNUP_TOKEN, NOW)).thenThrow(invalid);

        assertThatThrownBy(
                        () ->
                                service.createAccount(
                                        socialUser(), SIGNUP_TOKEN, "01012345678", "길동"))
                .isSameAs(invalid);
        verifyNoInteractions(userRepository, socialAccountRepository, tokenIssuer);
    }

    @Test
    @DisplayName("LINKED SocialAccount는 User·SocialAccount·token 저장 전에 가입을 거부한다")
    void createAccount_linkedAccount_rejectsBeforePersistence() {
        when(socialAccountIdentityService.findByProviderAndKey(any(), any()))
                .thenReturn(Optional.of(linkedSocialAccount()));

        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.ALREADY_REGISTERED);
        verifyNoInteractions(userRepository, socialAccountRepository, tokenIssuer);
    }

    @Test
    @DisplayName("UNLINK_PENDING와 UNLINKED 계정은 저장 없이 재가입을 거부한다")
    void createAccount_nonLinkedAccount_rejectsBeforePersistence() {
        SocialAccount account = linkedSocialAccount();
        account.markUnlinkPending(NOW.minusMinutes(2));
        when(socialAccountIdentityService.findByProviderAndKey(any(), any()))
                .thenReturn(Optional.of(account));

        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
        verifyNoInteractions(userRepository, socialAccountRepository, tokenIssuer);

        account.markUnlinked(NOW.minusMinutes(1));
        assertErrorCode(
                () -> service.createAccount(socialUser(), SIGNUP_TOKEN, "01012345678", "길동"),
                ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
        verifyNoInteractions(userRepository, socialAccountRepository, tokenIssuer);
    }

    @Test
    @DisplayName("provider key UNIQUE 충돌은 immutable identity를 담은 전용 예외로 변환한다")
    void createAccount_providerKeyConflict_throwsIdentityConflict() {
        DataIntegrityViolationException exception =
                providerConflict("uk_social_account_provider_key");

        KakaoSignupIdentityConflictException conflict = assertIdentityConflict(exception);

        assertThat(conflict.identity()).isEqualTo(IDENTITY);
        assertThat(target.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
        assertThat(sibling.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("legacy provider ID UNIQUE 충돌도 동일 identity 전용 예외로 변환한다")
    void createAccount_legacyProviderIdConflict_throwsIdentityConflict() {
        DataIntegrityViolationException exception =
                providerConflict("uk_social_account_provider_user");

        KakaoSignupIdentityConflictException conflict = assertIdentityConflict(exception);

        assertThat(conflict.identity()).isEqualTo(IDENTITY);
    }

    @Test
    @DisplayName("관련 없는 SocialAccount constraint는 원본 예외를 그대로 다시 던진다")
    void createAccount_nonProviderKeyConflict_rethrowsOriginal() {
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_user_provider'");
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class))).thenThrow(exception);

        assertThatThrownBy(() -> service.createAccount(user, SIGNUP_TOKEN, "01012345678", "길동"))
                .isSameAs(exception);
        assertThat(target.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
        assertThat(sibling.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
        verify(tokenIssuer, never()).issue(any());
    }

    private KakaoSignupIdentityConflictException assertIdentityConflict(
            DataIntegrityViolationException exception) {
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class))).thenThrow(exception);

        return (KakaoSignupIdentityConflictException)
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> service.createAccount(user, SIGNUP_TOKEN, "01012345678", "길동"));
    }

    private DataIntegrityViolationException providerConflict(String constraintName) {
        return new DataIntegrityViolationException(
                "Duplicate entry for key 'social_account." + constraintName + "'");
    }

    private SocialSignupSession session(String tokenHash) {
        return SocialSignupSession.createKakao(
                tokenHash,
                IDENTIFIER,
                ENCRYPTED_PROVIDER_USER_ID,
                NOW.plusMinutes(15),
                NOW.minusMinutes(1));
    }

    private SocialAccount linkedSocialAccount() {
        return SocialAccount.createLinked(
                socialUser(),
                SocialProvider.KAKAO,
                PROVIDER_USER_ID,
                PROVIDER_USER_KEY,
                3,
                ENCRYPTED_PROVIDER_USER_ID,
                NOW.minusMinutes(3));
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
