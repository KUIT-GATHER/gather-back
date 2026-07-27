package com.gather.gather.domain.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 후 유예 기간 정책. 익명화 시점과 재가입 허용 시점이 같은 기준이어야 하므로 한 곳에 둔다.
 *
 * <p>유예를 두는 이유는 재가입 제한을 강제할 수단을 남기기 위해서다. 익명화하면 원 소유자를 식별할 수 없어 "탈퇴 후 7일간 재가입 불가"를 확인할 방법이 사라진다(PM
 * 결정).
 */
@Component
public class WithdrawalPolicy {

    private static final Duration GRACE_PERIOD = Duration.ofDays(7);

    /** 유예가 끝난 계정을 찾기 위한 기준 시각. 이 시각 이전에 탈퇴한 계정이 대상이다. */
    public LocalDateTime graceExpiryThreshold(LocalDateTime now) {
        return now.minus(GRACE_PERIOD);
    }

    /** 경계(정확히 7일)는 경과로 본다. 하루 단위 배치라 경계에 걸린 건을 하루 더 미룰 이유가 없다. */
    public boolean isGracePeriodOver(LocalDateTime withdrawnAt, LocalDateTime now) {
        return !now.isBefore(withdrawnAt.plus(GRACE_PERIOD));
    }
}
