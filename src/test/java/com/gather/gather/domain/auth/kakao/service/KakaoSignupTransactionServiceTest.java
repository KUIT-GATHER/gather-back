package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenPayload;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.SocialAccountConstraintResolver;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.util.List;
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
    private static final RejoinBlockIdentifier IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, PROVIDER_USER_KEY, 3);
    private static final EncryptedProviderUserId ENCRYPTED_PROVIDER_USER_ID =
            new EncryptedProviderUserId("ciphertext", 4);
    private static final SocialSignupTokenPayload PAYLOAD =
            new SocialSignupTokenPayload(
                    SocialProvider.KAKAO, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private SignupValidator signupValidator;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private SocialAccountProviderIdCipher providerIdCipher;

    private KakaoSignupTransactionService service;

    @BeforeEach
    void setUp() {
        service =
                new KakaoSignupTransactionService(
                        userRepository,
                        socialAccountRepository,
                        signupValidator,
                        tokenIssuer,
                        providerIdCipher,
                        new SocialAccountConstraintResolver());
    }

    @Test
    @DisplayName("가입 트랜잭션은 HMAC·암호문·key version·LINKED generation 1을 저장한다")
    void createAccount_savesLifecycleIdentity() {
        User user = socialUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        when(tokenIssuer.issue(user))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        TokenIssueResult result = service.createAccount(user, PAYLOAD, "01012345678", "길동");

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).saveAndFlush(captor.capture());
        SocialAccount account = captor.getValue();
        assertThat(account.getUser()).isSameAs(user);
        assertThat(account.getProviderUserKey()).isEqualTo(PROVIDER_USER_KEY);
        assertThat(account.getProviderUserKeyVersion()).isEqualTo(3);
        assertThat(account.getProviderUserIdCiphertext()).isEqualTo("ciphertext");
        assertThat(account.getEncryptionKeyVersion()).isEqualTo(4);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(account.getGeneration()).isEqualTo(1L);
        assertThat(ReflectionTestUtils.getField(account, "legacyProviderUserId"))
                .isEqualTo(PROVIDER_USER_ID);
        assertThat(result.accessToken()).isEqualTo("access-token");
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

        assertThatThrownBy(() -> service.createAccount(user, PAYLOAD, "01012345678", "길동"))
                .isInstanceOf(SocialAccountProviderKeyConflictException.class)
                .hasCause(exception);
    }

    @Test
    @DisplayName("dual-write 기간의 legacy provider ID UNIQUE 충돌도 동일 계정 경쟁으로 분류한다")
    void createAccount_legacyProviderIdConflict_throwsClassifiedException() {
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_provider_user'");
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class))).thenThrow(exception);

        assertThatThrownBy(() -> service.createAccount(user, PAYLOAD, "01012345678", "길동"))
                .isInstanceOf(SocialAccountProviderKeyConflictException.class)
                .hasCause(exception);
    }

    @Test
    @DisplayName("User provider 충돌과 식별 불가 무결성 오류는 원본을 숨기지 않는다")
    void createAccount_nonProviderKeyConflict_rethrowsOriginal() {
        User user = socialUser();
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(providerIdCipher.decrypt(ENCRYPTED_PROVIDER_USER_ID)).thenReturn(PROVIDER_USER_ID);
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_user_provider'");
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class))).thenThrow(exception);

        assertThatThrownBy(() -> service.createAccount(user, PAYLOAD, "01012345678", "길동"))
                .isSameAs(exception);
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
}
