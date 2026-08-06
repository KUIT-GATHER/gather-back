package com.gather.gather.domain.auth.kakao.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kakao-unlink-canary")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gather.kakao.unlink-canary.enabled", havingValue = "true")
public class KakaoUnlinkCanaryCommandRunner implements ApplicationRunner {

    private final KakaoUnlinkCanaryCommandExecutor executor;
    private final ConfigurableApplicationContext applicationContext;
    private final ProcessTerminator processTerminator;

    @Override
    public void run(ApplicationArguments arguments) {
        int exitCode = executor.execute();
        try {
            applicationContext.close();
        } catch (RuntimeException exception) {
            log.error(
                    "Kakao unlink canary context shutdown failed: failureType={}",
                    exception.getClass().getName());
            exitCode = KakaoUnlinkCanaryCommandExecutor.EXIT_EXECUTION_FAILURE;
        }
        processTerminator.terminate(exitCode);
    }
}
