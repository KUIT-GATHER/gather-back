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

class KakaoUnlinkCanaryCommandRunnerTest {

    @Test
    void closesContextBeforeTerminatingWithExecutorExitCode() {
        KakaoUnlinkCanaryCommandExecutor executor = mock(KakaoUnlinkCanaryCommandExecutor.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ProcessTerminator terminator = mock(ProcessTerminator.class);
        when(executor.execute()).thenReturn(KakaoUnlinkCanaryCommandExecutor.EXIT_RETRY_SCHEDULED);
        KakaoUnlinkCanaryCommandRunner runner =
                new KakaoUnlinkCanaryCommandRunner(executor, context, terminator);

        runner.run(new DefaultApplicationArguments(new String[0]));

        InOrder order = inOrder(executor, context, terminator);
        order.verify(executor).execute();
        order.verify(context).close();
        order.verify(terminator).terminate(KakaoUnlinkCanaryCommandExecutor.EXIT_RETRY_SCHEDULED);
    }

    @Test
    void contextCloseFailure_terminatesWithExecutionFailure() {
        KakaoUnlinkCanaryCommandExecutor executor = mock(KakaoUnlinkCanaryCommandExecutor.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ProcessTerminator terminator = mock(ProcessTerminator.class);
        when(executor.execute()).thenReturn(KakaoUnlinkCanaryCommandExecutor.EXIT_SUCCESS);
        doThrow(new IllegalStateException("close failed")).when(context).close();
        KakaoUnlinkCanaryCommandRunner runner =
                new KakaoUnlinkCanaryCommandRunner(executor, context, terminator);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(terminator).terminate(KakaoUnlinkCanaryCommandExecutor.EXIT_EXECUTION_FAILURE);
    }
}
