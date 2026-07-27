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

/**
 * 탈퇴한 사용자의 카카오 연결을 끊는다. 계정 종료가 커밋된 뒤에 호출해야 한다 — 커밋 전에 호출하면 탈퇴가 롤백돼도 카카오 연결은 이미 끊긴 상태가 된다.
 *
 * <p>사용자가 카카오에서 직접 연결을 끊어 들어온 웹훅 경로는 이 서비스를 거치지 않는다. 카카오가 이미 끊은 것을 다시 끊을 이유가 없다.
 *
 * <p>트랜잭션을 걸지 않는다. 카카오 호출이 최대 5초까지 걸리는데 그동안 DB 커넥션을 붙들고 있을 이유가 없고, 조회와 삭제는 각각 짧은 트랜잭션으로 끝나면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoUnlinkService {

    private final KakaoApiClient kakaoApiClient;
    private final SocialAccountRepository socialAccountRepository;

    /**
     * 일시 실패면 {@code social_account} row를 남긴다. 이 row 자체가 재처리 큐라서, 별도 상태 컬럼 없이 스케줄러가 남은 행을 다시 시도할 수
     * 있다. 반대로 성공·영구 실패는 다시 시도할 이유가 없으므로 지운다.
     */
    public void unlinkIfLinked(Long userId) {
        Optional<SocialAccount> socialAccount =
                socialAccountRepository.findByUserIdAndProvider(userId, SocialProvider.KAKAO);
        if (socialAccount.isEmpty()) {
            return;
        }

        SocialAccount account = socialAccount.get();
        KakaoUnlinkResult result = kakaoApiClient.unlink(account.getProviderUserId());
        if (result == KakaoUnlinkResult.TRANSIENT_FAILURE) {
            log.warn("카카오 연결 해제를 나중에 다시 시도합니다. userId={}", userId);
            return;
        }
        if (result == KakaoUnlinkResult.PERMANENT_FAILURE) {
            log.error("카카오 연결 해제가 영구 실패해 연동 정보만 정리합니다. userId={}", userId);
        }
        socialAccountRepository.delete(account);
    }
}
