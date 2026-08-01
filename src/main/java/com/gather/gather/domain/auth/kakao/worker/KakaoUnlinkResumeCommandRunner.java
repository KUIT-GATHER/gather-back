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
@Profile("kakao-unlink-resume")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gather.kakao.unlink-resume.enabled", havingValue = "true")
public class KakaoUnlinkResumeCommandRunner implements ApplicationRunner {

    private final KakaoUnlinkResumeCommandExecutor executor;
    private final ConfigurableApplicationContext applicationContext;
    private final ProcessTerminator processTerminator;

    @Override
    public void run(ApplicationArguments arguments) {
        int exitCode = executor.execute();
        try {
            applicationContext.close();
        } catch (RuntimeException exception) {
            log.error(
                    "Kakao unlink resume context shutdown failed: failureType={}",
                    exception.getClass().getSimpleName());
            exitCode = KakaoUnlinkResumeCommandExecutor.EXIT_EXECUTION_FAILURE;
        }
        processTerminator.terminate(exitCode);
    }
}
