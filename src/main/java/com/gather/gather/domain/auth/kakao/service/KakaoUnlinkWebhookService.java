package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.service.AccountTerminationService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoUnlinkWebhookService {

    private static final String ADMIN_KEY_PREFIX = "KakaoAK ";

    private final KakaoProperties kakaoProperties;
    private final SocialAccountRepository socialAccountRepository;
    private final AccountTerminationService accountTerminationService;

    @Transactional
    public void handleUnlink(String authorizationHeader, String appId, String kakaoUserId) {
        verifyAdminKey(authorizationHeader);

        if (!kakaoProperties.appId().equals(appId)) {
            log.warn("Kakao unlink webhook app id does not match. appId={}", appId);
            return;
        }

        Optional<SocialAccount> socialAccount =
                socialAccountRepository.findByProviderAndProviderUserIdForUpdate(
                        SocialProvider.KAKAO, kakaoUserId);
        if (socialAccount.isEmpty()) {
            log.info(
                    "Kakao unlink webhook has no matching social account. kakaoUserId={}",
                    kakaoUserId);
            return;
        }

        SocialAccount account = socialAccount.get();
        accountTerminationService.terminate(
                account.getUser().getId(), WithdrawalReason.KAKAO_UNLINK);
        socialAccountRepository.delete(account);
    }

    private void verifyAdminKey(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(ADMIN_KEY_PREFIX)) {
            log.warn("Kakao unlink webhook has no admin authorization header.");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String received = authorizationHeader.substring(ADMIN_KEY_PREFIX.length());
        boolean matched =
                MessageDigest.isEqual(
                        received.getBytes(StandardCharsets.UTF_8),
                        kakaoProperties.adminKey().getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            log.warn("Kakao unlink webhook admin key does not match.");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
