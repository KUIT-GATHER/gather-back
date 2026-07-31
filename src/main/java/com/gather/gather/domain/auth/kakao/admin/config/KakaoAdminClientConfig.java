package com.gather.gather.domain.auth.kakao.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 카카오 Admin API가 활성화된 환경에만 전용 HTTP client를 등록한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "kakao.admin", name = "enabled", havingValue = "true")
public class KakaoAdminClientConfig {

    public static final String REST_CLIENT_BEAN_NAME = "kakaoAdminRestClient";

    @Bean(REST_CLIENT_BEAN_NAME)
    RestClient kakaoAdminRestClient(
            RestClient.Builder restClientBuilder, KakaoAdminProperties properties) {
        return restClientBuilder
                .clone()
                .baseUrl(properties.apiBaseUrl().toString())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    KakaoAdminApiClient kakaoAdminApiClient(
            @Qualifier(REST_CLIENT_BEAN_NAME) RestClient restClient,
            ObjectMapper objectMapper,
            KakaoAdminProperties properties) {
        return new KakaoAdminApiClient(restClient, objectMapper, properties.key());
    }

    static ClientHttpRequestFactorySettings requestFactorySettings(
            KakaoAdminProperties properties) {
        return ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
    }

    private static ClientHttpRequestFactory requestFactory(KakaoAdminProperties properties) {
        // simple request factory에는 자동 retry가 없으며, durable retry는 후속 worker만 담당한다.
        return ClientHttpRequestFactoryBuilder.simple().build(requestFactorySettings(properties));
    }
}
