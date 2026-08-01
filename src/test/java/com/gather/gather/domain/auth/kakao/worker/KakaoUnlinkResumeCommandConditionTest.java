package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gather.gather.global.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class KakaoUnlinkResumeCommandConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void profileOnly_doesNotRegisterCommandRunner() {
        contextRunner
                .withInitializer(
                        context ->
                                context.getEnvironment().setActiveProfiles("kakao-unlink-resume"))
                .withUserConfiguration(KakaoUnlinkResumeCommandRunner.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(KakaoUnlinkResumeCommandRunner.class));
    }

    @Test
    void enabledOnly_doesNotRegisterCommandRunner() {
        contextRunner
                .withPropertyValues("gather.kakao.unlink-resume.enabled=true")
                .withUserConfiguration(KakaoUnlinkResumeCommandRunner.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(KakaoUnlinkResumeCommandRunner.class));
    }

    @Test
    void profileAndEnabled_registerCommandRunner() {
        contextRunner
                .withInitializer(
                        context ->
                                context.getEnvironment().setActiveProfiles("kakao-unlink-resume"))
                .withPropertyValues("gather.kakao.unlink-resume.enabled=true")
                .withUserConfiguration(
                        CommandDependencies.class, KakaoUnlinkResumeCommandRunner.class)
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(KakaoUnlinkResumeCommandRunner.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CommandDependencies {

        @Bean
        KakaoUnlinkResumeCommandExecutor executor() {
            return mock(KakaoUnlinkResumeCommandExecutor.class);
        }

        @Bean
        ProcessTerminator processTerminator() {
            return mock(ProcessTerminator.class);
        }
    }

    @Test
    void resumeProfile_doesNotRegisterSchedulingInfrastructure() {
        contextRunner
                .withInitializer(
                        context ->
                                context.getEnvironment().setActiveProfiles("kakao-unlink-resume"))
                .withPropertyValues("gather.scheduling.enabled=true")
                .withUserConfiguration(SchedulingConfig.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(
                                                ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void normalProfile_keepsSchedulingEnabledByDefault() {
        contextRunner
                .withUserConfiguration(SchedulingConfig.class)
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
