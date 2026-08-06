package com.gather.gather.domain.auth.kakao.worker;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"kakao-unlink-resume", "kakao-unlink-canary"})
public class SystemProcessTerminator implements ProcessTerminator {

    @Override
    public void terminate(int exitCode) {
        System.exit(exitCode);
    }
}
