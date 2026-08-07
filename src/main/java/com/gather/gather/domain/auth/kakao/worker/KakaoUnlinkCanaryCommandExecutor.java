package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.kakao.admin.config.KakaoAdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Slf4j
@Component
@Profile("kakao-unlink-canary")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gather.kakao.unlink-canary.enabled", havingValue = "true")
public class KakaoUnlinkCanaryCommandExecutor {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_RETRY_SCHEDULED = 10;
    static final int EXIT_TASK_NOT_FOUND = 21;
    static final int EXIT_NOT_ELIGIBLE = 22;
    static final int EXIT_CLAIM_CONFLICT = 23;
    static final int EXIT_CONTROL_BLOCKED = 24;
    static final int EXIT_DEAD = 30;
    static final int EXIT_EXECUTION_FAILURE = 50;
    static final int EXIT_INPUT_OR_ENVIRONMENT = 60;

    private final ConfigurableApplicationContext applicationContext;
    private final Environment environment;
    private final KakaoUnlinkCanaryCommandProperties commandProperties;
    private final KakaoAdminProperties adminProperties;
    private final KakaoUnlinkWorkerProperties workerProperties;
    private final KakaoUnlinkClaimService claimService;
    private final ObjectProvider<KakaoUnlinkTaskProcessor> taskProcessorProvider;

    public int execute() {
        try {
            long taskId = validateAndParseTaskId();
            KakaoUnlinkTaskProcessor taskProcessor = taskProcessorProvider.getIfAvailable();
            if (taskProcessor == null) {
                throw new KakaoUnlinkCanaryCommandValidationException();
            }
            KakaoUnlinkSingleClaimResult claimResult = claimService.claimOne(taskId);
            if (claimResult.outcome() != KakaoUnlinkSingleClaimResult.Outcome.CLAIMED) {
                int exitCode = mapClaimExitCode(claimResult.outcome());
                log.warn(
                        "Kakao unlink canary claim rejected: taskId={}, outcome={}, exitCode={}",
                        taskId,
                        claimResult.outcome(),
                        exitCode);
                return exitCode;
            }

            KakaoUnlinkProcessingResult processingResult =
                    taskProcessor.process(claimResult.claim());
            int exitCode = mapProcessingExitCode(processingResult);
            log.info(
                    "Kakao unlink canary completed: taskId={}, outcome={}, exitCode={}",
                    taskId,
                    processingResult,
                    exitCode);
            return exitCode;
        } catch (KakaoUnlinkCanaryCommandValidationException exception) {
            log.error(
                    "Kakao unlink canary command rejected: failureType={}",
                    exception.getClass().getSimpleName());
            return EXIT_INPUT_OR_ENVIRONMENT;
        } catch (RuntimeException exception) {
            log.error(
                    "Kakao unlink canary command failed: failureType={}",
                    exception.getClass().getName());
            return EXIT_EXECUTION_FAILURE;
        }
    }

    private long validateAndParseTaskId() {
        if (applicationContext instanceof WebApplicationContext) {
            throw new KakaoUnlinkCanaryCommandValidationException();
        }
        String webApplicationType = environment.getProperty("spring.main.web-application-type");
        if (webApplicationType == null || !"none".equalsIgnoreCase(webApplicationType.trim())) {
            throw new KakaoUnlinkCanaryCommandValidationException();
        }
        if (!Boolean.FALSE.equals(
                environment.getProperty("gather.scheduling.enabled", Boolean.class))) {
            throw new KakaoUnlinkCanaryCommandValidationException();
        }
        if (!adminProperties.enabled() || workerProperties.enabled()) {
            throw new KakaoUnlinkCanaryCommandValidationException();
        }

        try {
            long taskId = Long.parseLong(commandProperties.taskId().trim());
            if (taskId <= 0) {
                throw new KakaoUnlinkCanaryCommandValidationException();
            }
            return taskId;
        } catch (NullPointerException | NumberFormatException exception) {
            throw new KakaoUnlinkCanaryCommandValidationException();
        }
    }

    private int mapClaimExitCode(KakaoUnlinkSingleClaimResult.Outcome outcome) {
        return switch (outcome) {
            case TASK_NOT_FOUND -> EXIT_TASK_NOT_FOUND;
            case NOT_PENDING, NOT_DUE -> EXIT_NOT_ELIGIBLE;
            case LOCK_CONFLICT -> EXIT_CLAIM_CONFLICT;
            case CONTROL_BLOCKED -> EXIT_CONTROL_BLOCKED;
            case INVARIANT_ERROR -> EXIT_EXECUTION_FAILURE;
            case CLAIMED -> throw new IllegalArgumentException("CLAIMED 결과는 processor로 전달해야 합니다.");
        };
    }

    private int mapProcessingExitCode(KakaoUnlinkProcessingResult result) {
        return switch (result) {
            case SUCCEEDED -> EXIT_SUCCESS;
            case RETRY_SCHEDULED -> EXIT_RETRY_SCHEDULED;
            case STALE, CLAIM_LOST -> EXIT_CLAIM_CONFLICT;
            case CONFIGURATION_BLOCKED -> EXIT_CONTROL_BLOCKED;
            case DEAD -> EXIT_DEAD;
        };
    }

    private static final class KakaoUnlinkCanaryCommandValidationException
            extends RuntimeException {}
}
