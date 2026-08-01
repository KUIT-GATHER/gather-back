package com.gather.gather.domain.auth.kakao.worker;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

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
    void contextCloseFailureTerminatesWithExecutionFailureCode() {
        KakaoUnlinkResumeCommandExecutor executor = mock(KakaoUnlinkResumeCommandExecutor.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ProcessTerminator terminator = mock(ProcessTerminator.class);
        when(executor.execute()).thenReturn(KakaoUnlinkResumeCommandExecutor.EXIT_SUCCESS);
        doThrow(new IllegalStateException("close failed")).when(context).close();
        KakaoUnlinkResumeCommandRunner runner =
                new KakaoUnlinkResumeCommandRunner(executor, context, terminator);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(terminator).terminate(KakaoUnlinkResumeCommandExecutor.EXIT_EXECUTION_FAILURE);
    }
}
