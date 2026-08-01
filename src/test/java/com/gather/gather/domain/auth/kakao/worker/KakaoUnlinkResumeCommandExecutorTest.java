package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.context.ConfigurableWebApplicationContext;

class KakaoUnlinkResumeCommandExecutorTest {

    @Test
    void validCommand_normalizesTaskIdsAndCallsService() {
        KakaoUnlinkWorkerResumeService resumeService = mock(KakaoUnlinkWorkerResumeService.class);
        when(resumeService.resumeConfigurationTasks(
                        List.of(123L, 124L),
                        "operator-name",
                        KakaoUnlinkResumeReason.ADMIN_KEY_CORRECTED))
                .thenReturn(2);
        KakaoUnlinkResumeCommandExecutor executor =
                executor(
                        mock(ConfigurableApplicationContext.class),
                        environmentWithSchedulingDisabled(),
                        new KakaoUnlinkResumeCommandProperties(
                                true, "124, 123,124", " operator-name ", "admin_key_corrected"),
                        disabledWorkerProperties(),
                        resumeService);

        assertThat(executor.execute()).isEqualTo(KakaoUnlinkResumeCommandExecutor.EXIT_SUCCESS);
        verify(resumeService)
                .resumeConfigurationTasks(
                        List.of(123L, 124L),
                        "operator-name",
                        KakaoUnlinkResumeReason.ADMIN_KEY_CORRECTED);
    }

    @Test
    void missingInput_returnsEnvironmentExitWithoutCallingService() {
        KakaoUnlinkWorkerResumeService resumeService = mock(KakaoUnlinkWorkerResumeService.class);
        KakaoUnlinkResumeCommandExecutor executor =
                executor(
                        mock(ConfigurableApplicationContext.class),
                        environmentWithSchedulingDisabled(),
                        new KakaoUnlinkResumeCommandProperties(true, "", "operator", ""),
                        disabledWorkerProperties(),
                        resumeService);

        assertThat(executor.execute())
                .isEqualTo(KakaoUnlinkResumeCommandExecutor.EXIT_INPUT_OR_ENVIRONMENT);
        verify(resumeService, never())
                .resumeConfigurationTasks(
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void webContext_returnsEnvironmentExitWithoutCallingService() {
        assertUnsafeEnvironmentRejected(
                mock(ConfigurableWebApplicationContext.class), false, false);
    }

    @Test
    void schedulingEnabled_returnsEnvironmentExitWithoutCallingService() {
        assertUnsafeEnvironmentRejected(mock(ConfigurableApplicationContext.class), true, false);
    }

    @Test
    void workerEnabled_returnsEnvironmentExitWithoutCallingService() {
        assertUnsafeEnvironmentRejected(mock(ConfigurableApplicationContext.class), false, true);
    }

    @Test
    void invariantFailure_returnsInvariantExit() {
        KakaoUnlinkWorkerResumeService resumeService = mock(KakaoUnlinkWorkerResumeService.class);
        when(resumeService.resumeConfigurationTasks(
                        List.of(123L), "operator", KakaoUnlinkResumeReason.CONFIGURATION_VERIFIED))
                .thenThrow(new KakaoUnlinkResumeInvariantException("invalid state"));
        KakaoUnlinkResumeCommandExecutor executor = validExecutor(resumeService);

        assertThat(executor.execute()).isEqualTo(KakaoUnlinkResumeCommandExecutor.EXIT_INVARIANT);
    }

    @Test
    void unexpectedFailure_returnsExecutionFailureExit() {
        KakaoUnlinkWorkerResumeService resumeService = mock(KakaoUnlinkWorkerResumeService.class);
        when(resumeService.resumeConfigurationTasks(
                        List.of(123L), "operator", KakaoUnlinkResumeReason.CONFIGURATION_VERIFIED))
                .thenThrow(new IllegalStateException("transaction failed"));
        KakaoUnlinkResumeCommandExecutor executor = validExecutor(resumeService);

        assertThat(executor.execute())
                .isEqualTo(KakaoUnlinkResumeCommandExecutor.EXIT_EXECUTION_FAILURE);
    }

    private void assertUnsafeEnvironmentRejected(
            ConfigurableApplicationContext context,
            boolean schedulingEnabled,
            boolean workerEnabled) {
        KakaoUnlinkWorkerResumeService resumeService = mock(KakaoUnlinkWorkerResumeService.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("gather.scheduling.enabled", Boolean.toString(schedulingEnabled));
        KakaoUnlinkResumeCommandExecutor executor =
                executor(
                        context,
                        environment,
                        validProperties(),
                        workerProperties(workerEnabled),
                        resumeService);

        assertThat(executor.execute())
                .isEqualTo(KakaoUnlinkResumeCommandExecutor.EXIT_INPUT_OR_ENVIRONMENT);
        verify(resumeService, never())
                .resumeConfigurationTasks(
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    private KakaoUnlinkResumeCommandExecutor validExecutor(
            KakaoUnlinkWorkerResumeService resumeService) {
        return executor(
                mock(ConfigurableApplicationContext.class),
                environmentWithSchedulingDisabled(),
                validProperties(),
                disabledWorkerProperties(),
                resumeService);
    }

    private KakaoUnlinkResumeCommandProperties validProperties() {
        return new KakaoUnlinkResumeCommandProperties(
                true, "123", "operator", "CONFIGURATION_VERIFIED");
    }

    private MockEnvironment environmentWithSchedulingDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("gather.scheduling.enabled", "false");
        return environment;
    }

    private KakaoUnlinkWorkerProperties disabledWorkerProperties() {
        return workerProperties(false);
    }

    private KakaoUnlinkWorkerProperties workerProperties(boolean enabled) {
        return new KakaoUnlinkWorkerProperties(
                enabled,
                Duration.ofSeconds(30),
                10,
                Duration.ofMinutes(2),
                12,
                Duration.ofMinutes(1),
                Duration.ofHours(6),
                "test");
    }

    private KakaoUnlinkResumeCommandExecutor executor(
            ConfigurableApplicationContext context,
            MockEnvironment environment,
            KakaoUnlinkResumeCommandProperties properties,
            KakaoUnlinkWorkerProperties workerProperties,
            KakaoUnlinkWorkerResumeService resumeService) {
        return new KakaoUnlinkResumeCommandExecutor(
                context, environment, properties, workerProperties, resumeService);
    }
}
