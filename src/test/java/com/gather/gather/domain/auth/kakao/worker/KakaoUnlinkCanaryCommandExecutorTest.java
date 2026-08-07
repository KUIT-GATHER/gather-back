package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.kakao.admin.config.KakaoAdminProperties;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.context.ConfigurableWebApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class KakaoUnlinkCanaryCommandExecutorTest {

    @ParameterizedTest
    @MethodSource("claimExitCodes")
    void claimOutcome_mapsToExitCode(
            KakaoUnlinkSingleClaimResult.Outcome outcome, int expectedExitCode) {
        KakaoUnlinkClaimService claimService = mock(KakaoUnlinkClaimService.class);
        when(claimService.claimOne(123L)).thenReturn(KakaoUnlinkSingleClaimResult.of(outcome));
        KakaoUnlinkTaskProcessor processor = mock(KakaoUnlinkTaskProcessor.class);

        int exitCode = executor(claimService, processor).execute();

        assertThat(exitCode).isEqualTo(expectedExitCode);
        verify(processor, never()).process(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("processingExitCodes")
    void processingOutcome_mapsToExitCode(
            KakaoUnlinkProcessingResult outcome, int expectedExitCode) {
        KakaoUnlinkClaimService claimService = mock(KakaoUnlinkClaimService.class);
        KakaoUnlinkTaskProcessor processor = mock(KakaoUnlinkTaskProcessor.class);
        KakaoUnlinkClaim claim = claim();
        when(claimService.claimOne(123L)).thenReturn(KakaoUnlinkSingleClaimResult.claimed(claim));
        when(processor.process(claim)).thenReturn(outcome);

        assertThat(executor(claimService, processor).execute()).isEqualTo(expectedExitCode);
    }

    @Test
    void invalidTaskId_returnsEnvironmentExitWithoutClaim() {
        KakaoUnlinkClaimService claimService = mock(KakaoUnlinkClaimService.class);

        int exitCode =
                executor(
                                mock(ConfigurableApplicationContext.class),
                                safeEnvironment(),
                                new KakaoUnlinkCanaryCommandProperties(true, "0"),
                                enabledAdminProperties(),
                                workerProperties(false),
                                claimService,
                                processorProvider(mock(KakaoUnlinkTaskProcessor.class)))
                        .execute();

        assertThat(exitCode).isEqualTo(KakaoUnlinkCanaryCommandExecutor.EXIT_INPUT_OR_ENVIRONMENT);
        verify(claimService, never()).claimOne(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void missingProcessor_returnsEnvironmentExitWithoutClaim() {
        KakaoUnlinkClaimService claimService = mock(KakaoUnlinkClaimService.class);

        int exitCode =
                executor(
                                mock(ConfigurableApplicationContext.class),
                                safeEnvironment(),
                                new KakaoUnlinkCanaryCommandProperties(true, "123"),
                                enabledAdminProperties(),
                                workerProperties(false),
                                claimService,
                                processorProvider(null))
                        .execute();

        assertThat(exitCode).isEqualTo(KakaoUnlinkCanaryCommandExecutor.EXIT_INPUT_OR_ENVIRONMENT);
        verify(claimService, never()).claimOne(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void unsafeEnvironment_returnsEnvironmentExitWithoutClaim() {
        KakaoUnlinkClaimService claimService = mock(KakaoUnlinkClaimService.class);
        MockEnvironment schedulingEnabled = safeEnvironment();
        schedulingEnabled.setProperty("gather.scheduling.enabled", "true");

        assertEnvironmentRejected(
                mock(ConfigurableWebApplicationContext.class),
                safeEnvironment(),
                enabledAdminProperties(),
                workerProperties(false),
                claimService);
        assertEnvironmentRejected(
                mock(ConfigurableApplicationContext.class),
                schedulingEnabled,
                enabledAdminProperties(),
                workerProperties(false),
                claimService);
        assertEnvironmentRejected(
                mock(ConfigurableApplicationContext.class),
                safeEnvironment(),
                disabledAdminProperties(),
                workerProperties(false),
                claimService);
        assertEnvironmentRejected(
                mock(ConfigurableApplicationContext.class),
                safeEnvironment(),
                enabledAdminProperties(),
                workerProperties(true),
                claimService);

        verify(claimService, never()).claimOne(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void unexpectedFailure_returnsExecutionFailureWithoutClaimToken(CapturedOutput output) {
        KakaoUnlinkClaimService claimService = mock(KakaoUnlinkClaimService.class);
        KakaoUnlinkTaskProcessor processor = mock(KakaoUnlinkTaskProcessor.class);
        KakaoUnlinkClaim claim = claim();
        when(claimService.claimOne(123L)).thenReturn(KakaoUnlinkSingleClaimResult.claimed(claim));
        when(processor.process(claim))
                .thenThrow(new IllegalStateException("database failed " + claim.claimToken()));

        assertThat(executor(claimService, processor).execute())
                .isEqualTo(KakaoUnlinkCanaryCommandExecutor.EXIT_EXECUTION_FAILURE);
        assertThat(output)
                .contains("failureType=java.lang.IllegalStateException")
                .doesNotContain(claim.claimToken());
    }

    private void assertEnvironmentRejected(
            ConfigurableApplicationContext context,
            MockEnvironment environment,
            KakaoAdminProperties adminProperties,
            KakaoUnlinkWorkerProperties workerProperties,
            KakaoUnlinkClaimService claimService) {
        KakaoUnlinkCanaryCommandExecutor executor =
                executor(
                        context,
                        environment,
                        new KakaoUnlinkCanaryCommandProperties(true, "123"),
                        adminProperties,
                        workerProperties,
                        claimService,
                        processorProvider(mock(KakaoUnlinkTaskProcessor.class)));

        assertThat(executor.execute())
                .isEqualTo(KakaoUnlinkCanaryCommandExecutor.EXIT_INPUT_OR_ENVIRONMENT);
    }

    private KakaoUnlinkCanaryCommandExecutor executor(
            KakaoUnlinkClaimService claimService, KakaoUnlinkTaskProcessor processor) {
        return executor(
                mock(ConfigurableApplicationContext.class),
                safeEnvironment(),
                new KakaoUnlinkCanaryCommandProperties(true, " 123 "),
                enabledAdminProperties(),
                workerProperties(false),
                claimService,
                processorProvider(processor));
    }

    private KakaoUnlinkCanaryCommandExecutor executor(
            ConfigurableApplicationContext context,
            MockEnvironment environment,
            KakaoUnlinkCanaryCommandProperties properties,
            KakaoAdminProperties adminProperties,
            KakaoUnlinkWorkerProperties workerProperties,
            KakaoUnlinkClaimService claimService,
            ObjectProvider<KakaoUnlinkTaskProcessor> processorProvider) {
        return new KakaoUnlinkCanaryCommandExecutor(
                context,
                environment,
                properties,
                adminProperties,
                workerProperties,
                claimService,
                processorProvider);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<KakaoUnlinkTaskProcessor> processorProvider(
            KakaoUnlinkTaskProcessor processor) {
        ObjectProvider<KakaoUnlinkTaskProcessor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(processor);
        return provider;
    }

    private MockEnvironment safeEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.main.web-application-type", "none");
        environment.setProperty("gather.scheduling.enabled", "false");
        return environment;
    }

    private KakaoAdminProperties enabledAdminProperties() {
        return new KakaoAdminProperties(
                true,
                "test-admin-key",
                URI.create("https://kapi.kakao.com"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    private KakaoAdminProperties disabledAdminProperties() {
        return new KakaoAdminProperties(
                false,
                null,
                URI.create("https://kapi.kakao.com"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
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

    private KakaoUnlinkClaim claim() {
        return new KakaoUnlinkClaim(123L, 2L, 3L, 1L, "sensitive-claim-token", 0);
    }

    private static Stream<Arguments> claimExitCodes() {
        return Stream.of(
                Arguments.of(
                        KakaoUnlinkSingleClaimResult.Outcome.TASK_NOT_FOUND,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_TASK_NOT_FOUND),
                Arguments.of(
                        KakaoUnlinkSingleClaimResult.Outcome.NOT_PENDING,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_NOT_ELIGIBLE),
                Arguments.of(
                        KakaoUnlinkSingleClaimResult.Outcome.NOT_DUE,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_NOT_ELIGIBLE),
                Arguments.of(
                        KakaoUnlinkSingleClaimResult.Outcome.LOCK_CONFLICT,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_CLAIM_CONFLICT),
                Arguments.of(
                        KakaoUnlinkSingleClaimResult.Outcome.CONTROL_BLOCKED,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_CONTROL_BLOCKED),
                Arguments.of(
                        KakaoUnlinkSingleClaimResult.Outcome.INVARIANT_ERROR,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_EXECUTION_FAILURE));
    }

    private static Stream<Arguments> processingExitCodes() {
        return Stream.of(
                Arguments.of(
                        KakaoUnlinkProcessingResult.SUCCEEDED,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_SUCCESS),
                Arguments.of(
                        KakaoUnlinkProcessingResult.RETRY_SCHEDULED,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_RETRY_SCHEDULED),
                Arguments.of(
                        KakaoUnlinkProcessingResult.STALE,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_CLAIM_CONFLICT),
                Arguments.of(
                        KakaoUnlinkProcessingResult.CLAIM_LOST,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_CLAIM_CONFLICT),
                Arguments.of(
                        KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_CONTROL_BLOCKED),
                Arguments.of(
                        KakaoUnlinkProcessingResult.DEAD,
                        KakaoUnlinkCanaryCommandExecutor.EXIT_DEAD));
    }
}
