package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.service.AccountTerminationService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoUnlinkWebhookServiceTest {

    private static final String ADMIN_KEY = "test-kakao-admin-key-0123456789a";
    private static final String APP_ID = "1234567";
    private static final String AUTHORIZATION = "KakaoAK " + ADMIN_KEY;
    private static final String KAKAO_USER_ID = "4242";
    private static final Long USER_ID = 7L;

    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private AccountTerminationService accountTerminationService;

    private KakaoUnlinkWebhookService kakaoUnlinkWebhookService;

    @BeforeEach
    void setUp() {
        kakaoUnlinkWebhookService =
                new KakaoUnlinkWebhookService(
                        properties(), socialAccountRepository, accountTerminationService);
    }

    private KakaoProperties properties() {
        return new KakaoProperties(
                "test-rest-api-key",
                "test-client-secret",
                ADMIN_KEY,
                APP_ID,
                List.of("https://gathernow.kr/login/kakao/callback"),
                "z9tOf6reUdkTRI0KFFiydLKdxpayBBxVWSAm7EJTgKXolFCFvnQ4qViBrdh6y7yP",
                900,
                "https://kauth.kakao.com",
                "https://kapi.kakao.com");
    }

    private SocialAccount linkedAccount(UserStatus status) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        return SocialAccount.create(user, SocialProvider.KAKAO, KAKAO_USER_ID);
    }

    private void givenSocialAccount(Optional<SocialAccount> account) {
        when(socialAccountRepository.findByProviderAndProviderUserIdForUpdate(
                        SocialProvider.KAKAO, KAKAO_USER_ID))
                .thenReturn(account);
    }

    @Test
    @DisplayName("정상 웹훅은 카카오 연결 해제 사유로 계정을 종료하고 연동 정보를 지운다")
    void handleUnlink_terminatesAccountAndDeletesSocialAccount() {
        SocialAccount account = linkedAccount(UserStatus.ACTIVE);
        givenSocialAccount(Optional.of(account));

        kakaoUnlinkWebhookService.handleUnlink(AUTHORIZATION, APP_ID, KAKAO_USER_ID);

        verify(accountTerminationService).terminate(USER_ID, WithdrawalReason.KAKAO_UNLINK);
        verify(socialAccountRepository).delete(account);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "KakaoAK wrong-admin-key-0123456789", "Bearer " + ADMIN_KEY})
    @DisplayName("어드민 키가 없거나 다르면 401로 거부하고 아무것도 처리하지 않는다")
    void handleUnlink_withWrongAdminKey_throwsUnauthorized(String authorizationHeader) {
        assertThatThrownBy(
                        () ->
                                kakaoUnlinkWebhookService.handleUnlink(
                                        authorizationHeader, APP_ID, KAKAO_USER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(socialAccountRepository, accountTerminationService);
    }

    @Test
    @DisplayName("다른 앱의 웹훅은 설정 오류이므로 재전송을 유발하지 않고 무시한다")
    void handleUnlink_withOtherAppId_isNoOp() {
        assertThatCode(
                        () ->
                                kakaoUnlinkWebhookService.handleUnlink(
                                        AUTHORIZATION, "9999999", KAKAO_USER_ID))
                .doesNotThrowAnyException();

        verifyNoInteractions(socialAccountRepository, accountTerminationService);
    }

    @Test
    @DisplayName("가입을 마치지 않은 사용자는 연동 정보가 없는 것이 정상이라 그냥 넘어간다")
    void handleUnlink_withoutSocialAccount_isNoOp() {
        givenSocialAccount(Optional.empty());

        assertThatCode(
                        () ->
                                kakaoUnlinkWebhookService.handleUnlink(
                                        AUTHORIZATION, APP_ID, KAKAO_USER_ID))
                .doesNotThrowAnyException();

        verifyNoInteractions(accountTerminationService);
        verify(socialAccountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("이미 탈퇴한 계정이면 중복 웹훅이므로 아무것도 하지 않는다")
    void handleUnlink_whenAlreadyWithdrawn_isNoOp() {
        givenSocialAccount(Optional.of(linkedAccount(UserStatus.WITHDRAWN)));

        kakaoUnlinkWebhookService.handleUnlink(AUTHORIZATION, APP_ID, KAKAO_USER_ID);

        verify(accountTerminationService).terminate(USER_ID, WithdrawalReason.KAKAO_UNLINK);
        verify(socialAccountRepository).delete(any(SocialAccount.class));
    }
}
