package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class KakaoUnlinkWorkerSchedulerTest {

    @Test
    void unexpectedBatchFailure_logsStackTrace(CapturedOutput output) {
        KakaoUnlinkWorker worker = mock(KakaoUnlinkWorker.class);
        doThrow(new IllegalStateException("unexpected-scheduler-failure")).when(worker).runBatch();
        KakaoUnlinkWorkerScheduler scheduler = new KakaoUnlinkWorkerScheduler(worker);

        scheduler.poll();

        assertThat(output)
                .contains("failureType=java.lang.IllegalStateException")
                .contains("java.lang.IllegalStateException: unexpected-scheduler-failure");
    }
}
