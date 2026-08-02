package com.gather.gather.domain.auth.kakao.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("kakao-unlink-resume")
@ConditionalOnProperty(name = "gather.kakao.unlink-resume.enabled", havingValue = "true")
public class SystemProcessTerminator implements ProcessTerminator {

    @Override
    public void terminate(int exitCode) {
        System.exit(exitCode);
    }
}
