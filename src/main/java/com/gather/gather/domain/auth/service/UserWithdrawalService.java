package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 본인 탈퇴 진입점. 계정 종료 자체는 웹훅과 공유하는 {@link AccountTerminationService}가 수행한다. */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final AccountTerminationService accountTerminationService;

    // 여기에 @Transactional을 걸지 않아야 terminate가 자체 트랜잭션으로 커밋된다. 실패할 수 있는 외부 호출은 커밋 뒤에 붙인다.
    public void withdrawMyAccount() {
        accountTerminationService.terminate(SecurityUtil.getCurrentUserId(), WithdrawalReason.SELF);
    }
}
