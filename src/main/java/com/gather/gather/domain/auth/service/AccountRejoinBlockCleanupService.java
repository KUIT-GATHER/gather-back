package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재가입 차단이 끝나고 개인정보 보관기간(실제 탈퇴 완료 후 3개월)도 지난 재가입 제한 row를 파기한다. */
@Service
@RequiredArgsConstructor
public class AccountRejoinBlockCleanupService {

    private final AccountRejoinBlockRepository blockRepository;
    private final Clock clock;

    /** 한 실행의 두 경계 판정이 같은 기준 시각을 쓰도록 현재 시각을 한 번만 계산해 넘긴다. */
    @Transactional
    public int cleanupRetentionExpiredBlocks() {
        return blockRepository.deleteAllRetentionExpired(LocalDateTime.now(clock));
    }
}
