package com.gather.gather.domain.auth.scheduler;

import com.gather.gather.domain.auth.service.EmailVerificationCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 구 버전 JAR이 남긴 평문 인증 코드 행을 기동 시점에 파기한다.
 *
 * <p>실패를 삼키면 평문 행이 남은 채로 서비스가 열리므로 예외를 그대로 전파해 기동을 실패시킨다. 배포 스크립트는 헬스체크 실패를 보고 이전 JAR로 롤백한다.
 *
 * <p>이 러너는 준비 완료 이전 관문일 뿐이며, 웹 포트가 먼저 열릴 수 있으므로 요청 경로의 평문 행 차단을 대신하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationLegacyPurgeRunner implements ApplicationRunner {

    private final EmailVerificationCleanupService cleanupService;

    @Override
    public void run(ApplicationArguments arguments) {
        int deletedCount = cleanupService.purgeLegacyVerifications();
        log.info("Email verification legacy purge completed: deletedCount={}", deletedCount);
    }
}
