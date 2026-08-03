package com.gather.gather.domain.auth.kakao.worker;

public interface ProcessTerminator {

    void terminate(int exitCode);
}
