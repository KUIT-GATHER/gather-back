package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import com.gather.gather.domain.auth.kakao.admin.config.KakaoAdminClientConfig;
import com.gather.gather.domain.auth.kakao.admin.config.KakaoAdminProperties;
import com.gather.gather.global.config.ApplicationTimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class KakaoUnlinkWorkerConfigurationValidatorTest {

    private static final String TEST_ADMIN_KEY = "unit-test-admin-key";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    RestClientAutoConfiguration.class))
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void adminDisabledAndWorkerDisabled_startsWithoutAdminOrWorker() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(KakaoAdminApiClient.class);
                    assertThat(context).doesNotHaveBean(KakaoUnlinkWorker.class);
                    assertThat(context).doesNotHaveBean(KakaoUnlinkWorkerScheduler.class);
                    assertThat(context)
                            .doesNotHaveBean(KakaoUnlinkWorkerConfig.TASK_SCHEDULER_BEAN_NAME);
                });
    }

    @Test
    void adminEnabledAndWorkerDisabled_startsWithAdminOnly() {
        contextRunner
                .withPropertyValues("kakao.admin.enabled=true", "kakao.admin.key=" + TEST_ADMIN_KEY)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(KakaoAdminApiClient.class);
                            assertThat(context).doesNotHaveBean(KakaoUnlinkWorker.class);
                            assertThat(context).doesNotHaveBean(KakaoUnlinkWorkerScheduler.class);
                        });
    }

    @Test
    void adminDisabledAndWorkerEnabled_failsFast() {
        contextRunner
                .withPropertyValues("kakao.admin.unlink-worker.enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasRootCauseInstanceOf(IllegalStateException.class)
                                    .rootCause()
                                    .hasMessageContaining("kakao.admin.enabled=true")
                                    .hasMessageNotContaining(TEST_ADMIN_KEY);
                        });
    }

    @Test
    void adminEnabledAndWorkerEnabled_startsWithAdminAndWorker() {
        contextRunner
                .withPropertyValues(
                        "kakao.admin.enabled=true",
                        "kakao.admin.key=" + TEST_ADMIN_KEY,
                        "kakao.admin.unlink-worker.enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(KakaoAdminApiClient.class);
                            assertThat(context).hasSingleBean(KakaoUnlinkWorker.class);
                            assertThat(context).hasSingleBean(KakaoUnlinkWorkerScheduler.class);
                            assertThat(context)
                                    .hasBean(KakaoUnlinkWorkerConfig.TASK_SCHEDULER_BEAN_NAME);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({KakaoAdminProperties.class, KakaoUnlinkWorkerProperties.class})
    @Import({
        ApplicationTimeConfig.class,
        KakaoAdminClientConfig.class,
        KakaoUnlinkWorkerConfig.class,
        KakaoUnlinkWorker.class,
        KakaoUnlinkWorkerScheduler.class,
        KakaoUnlinkWorkerConfigurationValidator.class
    })
    static class TestConfiguration {

        @Bean
        KakaoUnlinkClaimService claimService() {
            return mock(KakaoUnlinkClaimService.class);
        }

        @Bean
        KakaoUnlinkTransactionService transactionService() {
            return mock(KakaoUnlinkTransactionService.class);
        }

        @Bean
        KakaoUnlinkResultService resultService() {
            return mock(KakaoUnlinkResultService.class);
        }

        @Bean
        KakaoUnlinkTaskProcessor taskProcessor() {
            return mock(KakaoUnlinkTaskProcessor.class);
        }
    }
}
