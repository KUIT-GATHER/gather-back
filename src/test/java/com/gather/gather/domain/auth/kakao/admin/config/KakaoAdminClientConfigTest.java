package com.gather.gather.domain.auth.kakao.admin.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

class KakaoAdminClientConfigTest {

    private static final String TEST_KEY = "unit-test-admin-key";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    RestClientAutoConfiguration.class))
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("기본 설정은 비활성화되고 key 없이 context가 정상 기동한다")
    void context_withDefaults_startsWithoutAdminClient() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KakaoAdminProperties.class);
                    assertThat(context.getBean(KakaoAdminProperties.class).enabled()).isFalse();
                    assertThat(context).doesNotHaveBean(KakaoAdminApiClient.class);
                    assertThat(context)
                            .doesNotHaveBean(KakaoAdminClientConfig.REST_CLIENT_BEAN_NAME);
                });
    }

    @Test
    @DisplayName("활성화된 유효 설정은 전용 RestClient와 Admin client를 등록한다")
    void context_withEnabledValidProperties_registersDedicatedClients() {
        contextRunner
                .withPropertyValues(
                        "kakao.admin.enabled=true",
                        "kakao.admin.key=" + TEST_KEY,
                        "kakao.admin.api-base-url=https://kapi.kakao.com",
                        "kakao.admin.connect-timeout=2s",
                        "kakao.admin.read-timeout=5s")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(KakaoAdminApiClient.class);
                            assertThat(
                                            context.getBean(
                                                    KakaoAdminClientConfig.REST_CLIENT_BEAN_NAME))
                                    .isInstanceOf(RestClient.class);

                            KakaoAdminProperties properties =
                                    context.getBean(KakaoAdminProperties.class);
                            ClientHttpRequestFactorySettings settings =
                                    KakaoAdminClientConfig.requestFactorySettings(properties);
                            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                            assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(5));
                        });
    }

    @Test
    @DisplayName("활성화 상태에서 key가 없으면 context 기동에 실패한다")
    void context_whenEnabledWithoutKey_failsFast() {
        contextRunner
                .withPropertyValues("kakao.admin.enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasRootCauseInstanceOf(IllegalStateException.class)
                                    .rootCause()
                                    .hasMessageContaining("KAKAO_ADMIN_KEY")
                                    .hasMessageNotContaining(TEST_KEY);
                        });
    }

    @Test
    @DisplayName("잘못된 base URL 문자열은 context 기동에 실패한다")
    void context_withMalformedBaseUrl_failsFast() {
        contextRunner
                .withPropertyValues("kakao.admin.api-base-url=not a uri")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KakaoAdminProperties.class)
    @Import(KakaoAdminClientConfig.class)
    static class TestConfiguration {}
}
