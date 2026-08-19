package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationCleanupService {

    // 1시간 fixedDelay의 실행시간·지연을 흡수해 정상 운영 중 24시간 내 파기되도록 여유를 둔다.
    static final int RETENTION_HOURS = 22;

    private final EmailVerificationRepository emailVerificationRepository;
    private final Clock clock;

    @Transactional
    public int cleanupOverdueVerifications() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(RETENTION_HOURS);
        return emailVerificationRepository.deleteAllCreatedAtOrBefore(cutoff);
    }

    /**
     * 현재 검증 방식으로 신뢰할 수 없는 평문 행을 파기한다.
     *
     * <p>대상은 두 갈래다. 하나는 최초 HMAC 전환 배포 이전부터 남아 있던 평문 행이고, 다른 하나는 새 버전 기동 실패 뒤 구 버전 JAR로 되돌아간 동안
     * 생성되거나 갱신된 행이다. 최초 전환에서도 파기가 일어나므로 아직 입력하지 않은 인증 코드와 인증 결과가 무효화될 수 있다.
     *
     * <p>보관 기간 정리와 삭제 사유가 달라 삭제 건수를 따로 반환한다. 기동 시점 호출자는 실패를 전파해 기동을 막고, 주기 실행 호출자는 실패를 흡수해 다음 주기에
     * 다시 시도한다.
     */
    @Transactional
    public int purgeLegacyVerifications() {
        return emailVerificationRepository.deleteAllLegacyFormat();
    }
}
