package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.service.KakaoUnlinkService;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 본인 탈퇴 진입점. 계정 종료 자체는 웹훅과 공유하는 {@link AccountTerminationService}가 수행한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final AccountTerminationService accountTerminationService;
    private final KakaoUnlinkService kakaoUnlinkService;

    // 여기에 @Transactional을 걸지 않아야 terminate가 자체 트랜잭션으로 커밋된다. 실패할 수 있는 외부 호출은 커밋 뒤에 붙인다.
    public void withdrawMyAccount() {
        Long userId = SecurityUtil.getCurrentUserId();
        accountTerminationService.terminate(userId, WithdrawalReason.SELF);

        // 여기서 예외가 나가면 탈퇴에 성공한 사용자가 에러 화면을 보게 된다. 되돌릴 수 없는 지점이므로 로그만 남기고 응답은 성공으로 둔다.
        try {
            kakaoUnlinkService.unlinkIfLinked(userId);
        } catch (RuntimeException exception) {
            log.error("탈퇴 후 카카오 연결 해제 처리에 실패했습니다. userId={}", userId, exception);
        }
    }
}
