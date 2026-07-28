package com.gather.gather.domain.auth.kakao.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.client.KakaoApiClient;
import com.gather.gather.domain.auth.kakao.client.KakaoUnlinkResult;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoUnlinkServiceTest {

    private static final Long USER_ID = 7L;
    private static final String PROVIDER_USER_ID = "4242";

    @Mock private KakaoApiClient kakaoApiClient;
    @Mock private SocialAccountRepository socialAccountRepository;

    @InjectMocks private KakaoUnlinkService kakaoUnlinkService;

    private SocialAccount socialAccount() {
        return SocialAccount.create(mock(User.class), SocialProvider.KAKAO, PROVIDER_USER_ID);
    }

    private void givenLinkedAccount(KakaoUnlinkResult result) {
        when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.KAKAO))
                .thenReturn(Optional.of(socialAccount()));
        when(kakaoApiClient.unlink(PROVIDER_USER_ID)).thenReturn(result);
    }

    @Test
    @DisplayName("일반 회원은 연동 정보가 없으므로 카카오를 호출하지 않는다")
    void unlinkIfLinked_withoutSocialAccount_doesNotCallKakao() {
        when(socialAccountRepository.findByUserIdAndProvider(USER_ID, SocialProvider.KAKAO))
                .thenReturn(Optional.empty());

        kakaoUnlinkService.unlinkIfLinked(USER_ID);

        verifyNoInteractions(kakaoApiClient);
        verify(socialAccountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("연결 해제에 성공하면 연동 정보를 지운다")
    void unlinkIfLinked_whenSuccess_deletesSocialAccount() {
        givenLinkedAccount(KakaoUnlinkResult.SUCCESS);

        kakaoUnlinkService.unlinkIfLinked(USER_ID);

        verify(socialAccountRepository).delete(any(SocialAccount.class));
    }

    @Test
    @DisplayName("영구 실패(4xx)는 다시 시도해도 소용없으므로 연동 정보를 지운다")
    void unlinkIfLinked_whenPermanentFailure_deletesSocialAccount() {
        givenLinkedAccount(KakaoUnlinkResult.ALREADY_UNLINKED);

        kakaoUnlinkService.unlinkIfLinked(USER_ID);

        verify(socialAccountRepository).delete(any(SocialAccount.class));
    }

    @Test
    @DisplayName("일시 실패(5xx·네트워크)는 연동 정보를 남겨 재처리 대상으로 둔다")
    void unlinkIfLinked_whenTransientFailure_keepsSocialAccount() {
        givenLinkedAccount(KakaoUnlinkResult.RETRYABLE_FAILURE);

        kakaoUnlinkService.unlinkIfLinked(USER_ID);

        verify(socialAccountRepository, never()).delete(any());
    }
}
