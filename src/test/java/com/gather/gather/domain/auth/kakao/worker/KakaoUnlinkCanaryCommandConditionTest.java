package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gather.gather.global.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class KakaoUnlinkCanaryCommandConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void profileOnly_doesNotRegisterCommandRunner() {
        contextRunner
                .withInitializer(
                        context ->
                                context.getEnvironment().setActiveProfiles("kakao-unlink-canary"))
                .withUserConfiguration(KakaoUnlinkCanaryCommandRunner.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(KakaoUnlinkCanaryCommandRunner.class));
    }

    @Test
    void enabledOnly_doesNotRegisterCommandRunner() {
        contextRunner
                .withPropertyValues("gather.kakao.unlink-canary.enabled=true")
                .withUserConfiguration(KakaoUnlinkCanaryCommandRunner.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(KakaoUnlinkCanaryCommandRunner.class));
    }

    @Test
    void profileAndEnabled_registerCommandRunner() {
        contextRunner
                .withInitializer(
                        context ->
                                context.getEnvironment().setActiveProfiles("kakao-unlink-canary"))
                .withPropertyValues("gather.kakao.unlink-canary.enabled=true")
                .withUserConfiguration(
                        CommandDependencies.class, KakaoUnlinkCanaryCommandRunner.class)
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(KakaoUnlinkCanaryCommandRunner.class));
    }

    @Test
    void canaryProfile_registersCommonTerminatorAndDisablesScheduling() {
        contextRunner
                .withInitializer(
                        context ->
                                context.getEnvironment().setActiveProfiles("kakao-unlink-canary"))
                .withPropertyValues("gather.scheduling.enabled=true")
                .withUserConfiguration(SystemProcessTerminator.class, SchedulingConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(SystemProcessTerminator.class);
                            assertThat(context)
                                    .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                        });
    }

    @Test
    void normalProfile_registersNeitherCommandTerminatorNorDisabledScheduling() {
        contextRunner
                .withUserConfiguration(SystemProcessTerminator.class, SchedulingConfig.class)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(SystemProcessTerminator.class);
                            assertThat(context)
                                    .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CommandDependencies {

        @Bean
        KakaoUnlinkCanaryCommandExecutor executor() {
            return mock(KakaoUnlinkCanaryCommandExecutor.class);
        }

        @Bean
        ProcessTerminator processTerminator() {
            return mock(ProcessTerminator.class);
        }
    }
}
