package com.gather.gather.domain.auth.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.service.AccountRejoinBlockCleanupService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AccountRejoinBlockCleanupSchedulerWiringTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(CleanupSchedulerConfiguration.class)
                    .withBean(
                            AccountRejoinBlockCleanupService.class,
                            () -> Mockito.mock(AccountRejoinBlockCleanupService.class));

    @Test
    void enabledProperty_registersCleanupScheduler() {
        contextRunner
                .withPropertyValues("gather.auth.rejoin-block.cleanup-scheduler-enabled=true")
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(AccountRejoinBlockCleanupScheduler.class));
    }

    @Test
    void disabledProperty_doesNotRegisterCleanupScheduler() {
        contextRunner
                .withPropertyValues("gather.auth.rejoin-block.cleanup-scheduler-enabled=false")
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(AccountRejoinBlockCleanupScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AccountRejoinBlockCleanupScheduler.class)
    static class CleanupSchedulerConfiguration {}
}
