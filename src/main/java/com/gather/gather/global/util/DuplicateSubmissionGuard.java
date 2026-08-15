package com.gather.gather.global.util;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 동일 사용자가 짧은 시간 안에 같은 작업(작성 버튼 연타 등)을 반복 요청하는 것을 차단한다.
 *
 * <p>단일 인스턴스 배포를 전제로 한 인메모리 가드다(서버가 여러 대로 늘어나면 Redis 등 공유 저장소 기반으로 교체해야 한다). 최초 요청 시각을 기준으로 쿨다운을
 * 적용하며, 쿨다운이 지나면 별도 해제 없이 자동으로 풀린다.
 */
@Component
public class DuplicateSubmissionGuard {

    private static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(3);

    private final Map<String, Instant> recentSubmissions = new ConcurrentHashMap<>();

    /** 기본 쿨다운(3초)으로 중복 요청 여부를 검사한다. 중복이면 예외를 던진다. */
    public void guard(String key) {
        guard(key, DEFAULT_COOLDOWN);
    }

    /** 지정한 쿨다운 동안 같은 key로 다시 들어오면 {@link ErrorCode#DUPLICATE_SUBMISSION}을 던진다. */
    public void guard(String key, Duration cooldown) {
        Instant now = Instant.now();
        Instant recorded =
                recentSubmissions.compute(
                        key,
                        (k, last) ->
                                (last != null && last.plus(cooldown).isAfter(now)) ? last : now);
        if (!recorded.equals(now)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMISSION);
        }
    }
}
