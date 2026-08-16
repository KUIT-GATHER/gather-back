package com.gather.gather.domain.auth.kakao.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KakaoUnlinkMonitoringJsonConfiguration {

    @Bean
    HibernatePropertiesCustomizer kakaoUnlinkMonitoringJsonFormatMapper(ObjectMapper objectMapper) {
        JacksonJsonFormatMapper formatMapper = new JacksonJsonFormatMapper(objectMapper);
        return properties -> properties.put(AvailableSettings.JSON_FORMAT_MAPPER, formatMapper);
    }
}
