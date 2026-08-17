package com.gather.gather.domain.auth.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.service.EmailVerificationCleanupService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class EmailVerificationCleanupSchedulerWiringTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(CleanupSchedulerConfiguration.class)
                    .withBean(
                            EmailVerificationCleanupService.class,
                            () -> Mockito.mock(EmailVerificationCleanupService.class));

    @Test
    void enabledProperty_registersCleanupScheduler() {
        contextRunner
                .withPropertyValues("gather.auth.email-verification.cleanup-scheduler-enabled=true")
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(EmailVerificationCleanupScheduler.class));
    }

    @Test
    void disabledProperty_doesNotRegisterCleanupScheduler() {
        contextRunner
                .withPropertyValues(
                        "gather.auth.email-verification.cleanup-scheduler-enabled=false")
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(EmailVerificationCleanupScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(EmailVerificationCleanupScheduler.class)
    static class CleanupSchedulerConfiguration {}
}
