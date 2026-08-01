package com.gather.gather.domain.auth.kakao.worker;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Slf4j
@Component
@Profile("kakao-unlink-resume")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gather.kakao.unlink-resume.enabled", havingValue = "true")
public class KakaoUnlinkResumeCommandExecutor {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INPUT_OR_ENVIRONMENT = 2;
    static final int EXIT_INVARIANT = 3;
    static final int EXIT_EXECUTION_FAILURE = 4;

    private static final int MAX_ACTOR_LENGTH = 64;
    private static final Pattern ACTOR_PATTERN = Pattern.compile("[A-Za-z0-9._@-]+");

    private final ConfigurableApplicationContext applicationContext;
    private final Environment environment;
    private final KakaoUnlinkResumeCommandProperties commandProperties;
    private final KakaoUnlinkWorkerProperties workerProperties;
    private final KakaoUnlinkWorkerResumeService resumeService;

    public int execute() {
        try {
            ResumeRequest request = validateAndNormalize();
            int resumedCount =
                    resumeService.resumeConfigurationTasks(
                            request.taskIds(), request.actor(), request.reason());
            log.warn(
                    "Kakao unlink resume command succeeded: taskCount={}, actor={}, reason={}",
                    resumedCount,
                    request.actor(),
                    request.reason());
            return EXIT_SUCCESS;
        } catch (KakaoUnlinkResumeCommandValidationException exception) {
            log.error(
                    "Kakao unlink resume command rejected: failureType={}",
                    exception.getClass().getSimpleName());
            return EXIT_INPUT_OR_ENVIRONMENT;
        } catch (KakaoUnlinkResumeInvariantException exception) {
            log.error(
                    "Kakao unlink resume command rejected by state invariant: failureType={}",
                    exception.getClass().getSimpleName());
            return EXIT_INVARIANT;
        } catch (RuntimeException exception) {
            log.error(
                    "Kakao unlink resume command failed: failureType={}",
                    exception.getClass().getSimpleName());
            return EXIT_EXECUTION_FAILURE;
        }
    }

    private ResumeRequest validateAndNormalize() {
        if (applicationContext instanceof WebApplicationContext) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
        if (!Boolean.FALSE.equals(
                environment.getProperty("gather.scheduling.enabled", Boolean.class))) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
        if (workerProperties.enabled()) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }

        List<Long> taskIds = parseTaskIds(commandProperties.taskIds());
        String actor = normalizeActor(commandProperties.actor());
        KakaoUnlinkResumeReason reason = parseReason(commandProperties.reason());
        return new ResumeRequest(taskIds, actor, reason);
    }

    private static List<Long> parseTaskIds(String rawTaskIds) {
        if (rawTaskIds == null || rawTaskIds.isBlank()) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
        try {
            List<Long> taskIds =
                    Arrays.stream(rawTaskIds.split(",", -1))
                            .map(String::trim)
                            .map(Long::valueOf)
                            .toList();
            if (taskIds.isEmpty() || taskIds.stream().anyMatch(id -> id <= 0)) {
                throw new KakaoUnlinkResumeCommandValidationException();
            }
            return taskIds.stream().distinct().sorted().toList();
        } catch (NumberFormatException exception) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
    }

    private static String normalizeActor(String rawActor) {
        if (rawActor == null) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
        String actor = rawActor.trim();
        if (actor.isEmpty()
                || actor.length() > MAX_ACTOR_LENGTH
                || !ACTOR_PATTERN.matcher(actor).matches()) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
        return actor;
    }

    private static KakaoUnlinkResumeReason parseReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
        try {
            return KakaoUnlinkResumeReason.valueOf(rawReason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new KakaoUnlinkResumeCommandValidationException();
        }
    }

    private record ResumeRequest(
            List<Long> taskIds, String actor, KakaoUnlinkResumeReason reason) {}

    private static final class KakaoUnlinkResumeCommandValidationException
            extends RuntimeException {}
}
