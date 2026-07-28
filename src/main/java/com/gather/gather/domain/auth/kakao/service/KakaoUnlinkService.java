package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.kakao.client.KakaoApiClient;
import com.gather.gather.domain.auth.kakao.client.KakaoUnlinkResult;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Deletes a local Kakao link only after Kakao confirms the unlink is complete. */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoUnlinkService {

    private final KakaoApiClient kakaoApiClient;
    private final SocialAccountRepository socialAccountRepository;

    public KakaoUnlinkResult unlinkIfLinked(Long userId) {
        Optional<SocialAccount> socialAccount =
                socialAccountRepository.findByUserIdAndProvider(userId, SocialProvider.KAKAO);
        if (socialAccount.isEmpty()) {
            return KakaoUnlinkResult.NOT_LINKED;
        }

        SocialAccount account = socialAccount.get();
        KakaoUnlinkResult result = kakaoApiClient.unlink(account.getProviderUserId());
        if (result == KakaoUnlinkResult.RETRYABLE_FAILURE) {
            log.warn("Kakao unlink will be retried. userId={}", userId);
            return result;
        }

        socialAccountRepository.delete(account);
        return result;
    }
}
