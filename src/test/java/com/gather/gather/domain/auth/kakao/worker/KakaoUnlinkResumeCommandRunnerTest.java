package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class KakaoUnlinkResumeCommandRunnerTest {

    @Test
    void closesContextBeforeTerminatingWithExecutorExitCode() {
        KakaoUnlinkResumeCommandExecutor executor = mock(KakaoUnlinkResumeCommandExecutor.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ProcessTerminator terminator = mock(ProcessTerminator.class);
        when(executor.execute()).thenReturn(KakaoUnlinkResumeCommandExecutor.EXIT_INVARIANT);
        KakaoUnlinkResumeCommandRunner runner =
                new KakaoUnlinkResumeCommandRunner(executor, context, terminator);

        runner.run(new DefaultApplicationArguments(new String[0]));

        InOrder order = inOrder(executor, context, terminator);
        order.verify(executor).execute();
        order.verify(context).close();
        order.verify(terminator).terminate(KakaoUnlinkResumeCommandExecutor.EXIT_INVARIANT);
    }

    @Test
    void contextCloseFailureTerminatesWithExecutionFailureCodeAndLogsStackTrace(
            CapturedOutput output) {
        KakaoUnlinkResumeCommandExecutor executor = mock(KakaoUnlinkResumeCommandExecutor.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ProcessTerminator terminator = mock(ProcessTerminator.class);
        when(executor.execute()).thenReturn(KakaoUnlinkResumeCommandExecutor.EXIT_SUCCESS);
        doThrow(new IllegalStateException("close failed")).when(context).close();
        KakaoUnlinkResumeCommandRunner runner =
                new KakaoUnlinkResumeCommandRunner(executor, context, terminator);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(terminator).terminate(KakaoUnlinkResumeCommandExecutor.EXIT_EXECUTION_FAILURE);
        assertThat(output)
                .contains("failureType=java.lang.IllegalStateException")
                .contains("java.lang.IllegalStateException: close failed");
    }
}
