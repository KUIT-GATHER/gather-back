package com.gather.gather.global.util;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 동일 사용자가 짧은 시간 안에 같은 작업(작성 버튼 연타 등)을 반복 요청하는 것을 차단한다.
 *
 * <p>단일 인스턴스 배포를 전제로 한 인메모리 가드다(서버가 여러 대로 늘어나면 Redis 등 공유 저장소 기반으로 교체해야 한다). 최초 요청 시각을 기준으로 쿨다운을
 * 적용하며, 쿨다운이 지나면 별도 해제 없이 자동으로 풀린다.
 *
 * <p><b>주의:</b> 이 가드는 진짜 멱등성(idempotency)을 보장하지 않는다. 최초 호출 시각부터 정해진 쿨다운 동안만 같은 key를 차단할 뿐, 첫 요청의
 * 처리가 실제로 끝났는지·성공했는지는 추적하지 않는다. 첫 요청 처리가 쿨다운보다 오래 걸리면 그 요청이 끝나기 전에도 다음 요청이 통과할 수 있다. "버튼 연타 완화"
 * 용도로만 쓰고, 네트워크 재시도까지 포함한 엄밀한 중복 생성 방지가 필요하면 Idempotency-Key 등 별도 설계가 필요하다.
 */
@Component
@RequiredArgsConstructor
public class DuplicateSubmissionGuard {

    private static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(3);

    /** 이 크기를 넘으면 다음 호출에서 만료된 항목을 정리한다(무제한 증가 방지용 임계값 트리거). */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;

    private final Map<String, Instant> recentSubmissions = new ConcurrentHashMap<>();

    /** 기본 쿨다운(3초)으로 중복 요청 여부를 검사한다. 중복이면 예외를 던진다. */
    public void guard(String key) {
        guard(key, DEFAULT_COOLDOWN);
    }

    /** 지정한 쿨다운 동안 같은 key로 다시 들어오면 {@link ErrorCode#DUPLICATE_SUBMISSION}을 던진다. */
    public void guard(String key, Duration cooldown) {
        Instant now = Instant.now(clock);
        AtomicBoolean admitted = new AtomicBoolean(false);

        // compute()는 key 단위로 원자적으로 실행되므로, 같은 key로 동시에 들어온 호출 중 정확히 하나만
        // admitted=true가 된다. 통과 여부를 Instant 값 동등성으로 판정하면 두 호출의 now가 우연히 같을 때
        // (해상도가 낮은 Clock 등) 잘못 통과할 수 있어, 별도 플래그로 판정한다.
        recentSubmissions.compute(
                key,
                (ignoredKey, last) -> {
                    if (last != null && last.plus(cooldown).isAfter(now)) {
                        return last;
                    }
                    admitted.set(true);
                    return now;
                });

        if (!admitted.get()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMISSION);
        }

        cleanupIfNeeded(now, cooldown);
    }

    /**
     * 맵이 임계값을 넘으면 만료된(쿨다운이 지난) 항목을 청소한다. 정상 사용에서는 (작업 종류 × 사용자 × 모임) 조합 수만큼만 늘어나지만, 존재하지 않는 리소스에 대한
     * 반복 요청 등으로 키가 계속 새로 생기는 상황에서도 맵이 무한정 커지지 않도록 하는 안전장치다.
     */
    private void cleanupIfNeeded(Instant now, Duration cooldown) {
        if (recentSubmissions.size() < CLEANUP_THRESHOLD) {
            return;
        }
        recentSubmissions
                .entrySet()
                .removeIf(entry -> !entry.getValue().plus(cooldown).isAfter(now));
    }
}
