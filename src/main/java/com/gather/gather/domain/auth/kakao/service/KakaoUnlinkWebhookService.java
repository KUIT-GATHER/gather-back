package com.gather.gather.domain.auth.kakao.service;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 카카오에서 직접 연결을 끊었을 때 오는 웹훅을 처리한다. 우리가 unlink API를 호출한 경우에는 웹훅이 오지 않으므로 두 경로가 서로를 트리거하지 않는다.
 *
 * <p>카카오에 되돌려줄 것이 없어 대부분의 이상 상황은 200으로 흡수한다. 재전송을 유발해 봐야 결과가 같기 때문이다. 어드민 키 불일치만 예외로, 카카오가 보낸 요청이
 * 아니라는 뜻이므로 401로 거부한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoUnlinkWebhookService {

    private static final String ADMIN_KEY_PREFIX = "KakaoAK ";

    private final KakaoProperties kakaoProperties;
    private final SocialAccountRepository socialAccountRepository;
    private final AccountTerminationService accountTerminationService;

    /**
     * 계정 종료와 연동 삭제를 한 트랜잭션에서 처리한다. 탈퇴 API 경로와 달리 외부 호출이 없어 중간에 실패할 여지가 없다.
     *
     * @param authorizationHeader {@code KakaoAK {어드민 키}}. 값 자체는 어떤 경로로도 로그에 남기지 않는다.
     */
    @Transactional
    public void handleUnlink(String authorizationHeader, String appId, String kakaoUserId) {
        verifyAdminKey(authorizationHeader);

        if (!kakaoProperties.appId().equals(appId)) {
            // 다른 앱의 웹훅을 우리가 받고 있다는 뜻이라 설정 오류지 공격이 아니다. 재전송을 유발할 이유가 없어 200으로 흡수한다.
            log.warn("우리 앱이 아닌 카카오 웹훅을 수신했습니다. appId={}", appId);
            return;
        }

        Optional<SocialAccount> socialAccount =
                socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.KAKAO, kakaoUserId);
        if (socialAccount.isEmpty()) {
            // 가입 토큰 단계에서 이탈한 사용자(INCOMPLETE_SIGN_UP)는 연동 정보가 없는 것이 정상이다.
            log.info("연동 정보가 없어 연결 해제 웹훅을 건너뜁니다. kakaoUserId={}", kakaoUserId);
            return;
        }

        SocialAccount account = socialAccount.get();
        User user = account.getUser();
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            log.info("이미 탈퇴한 계정이라 연결 해제 웹훅을 건너뜁니다. userId={}", user.getId());
            return;
        }

        accountTerminationService.terminate(user.getId(), WithdrawalReason.KAKAO_UNLINK);
        socialAccountRepository.delete(account);
    }

    private void verifyAdminKey(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(ADMIN_KEY_PREFIX)) {
            log.warn("카카오 연결 해제 웹훅에 어드민 키 헤더가 없습니다.");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String received = authorizationHeader.substring(ADMIN_KEY_PREFIX.length());
        // 비교 시간이 값에 따라 달라지지 않도록 한다. equals는 첫 불일치에서 멈춰 키를 한 글자씩 좁혀낼 여지를 준다.
        boolean matched =
                MessageDigest.isEqual(
                        received.getBytes(StandardCharsets.UTF_8),
                        kakaoProperties.adminKey().getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            log.warn("카카오 연결 해제 웹훅의 어드민 키가 일치하지 않습니다.");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
