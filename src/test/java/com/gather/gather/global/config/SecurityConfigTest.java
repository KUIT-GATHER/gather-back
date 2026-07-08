package com.gather.gather.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.auth.service.TokenProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigTest {

    private static final String FRONT_ORIGIN = "https://gathernow.kr";
    private static final String LOCAL_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "https://evil.example";

    private final SecurityConfig securityConfig =
            new SecurityConfig(
                    mock(TokenProvider.class),
                    new ObjectMapper(),
                    new CorsProperties(List.of(FRONT_ORIGIN, LOCAL_ORIGIN)));

    @Test
    @DisplayName("CORS는 설정된 exact origin만 허용하고 credentials를 허용한다")
    void corsConfiguration_allowsExactOriginsAndCredentials() {
        CorsConfiguration configuration = corsConfiguration();

        assertThat(configuration.getAllowedOrigins()).containsExactly(FRONT_ORIGIN, LOCAL_ORIGIN);
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.checkOrigin(FRONT_ORIGIN)).isEqualTo(FRONT_ORIGIN);
        assertThat(configuration.checkOrigin(LOCAL_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
        assertThat(configuration.checkOrigin(DISALLOWED_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("CORS preflight는 Authorization 헤더와 credentials 응답 헤더를 허용한다")
    void corsPreflight_allowsAuthorizationHeaderAndCredentials() throws Exception {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request =
                new MockHttpServletRequest("OPTIONS", "/api/v1/auth/reissue");
        request.addHeader(HttpHeaders.ORIGIN, FRONT_ORIGIN);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean handled =
                new DefaultCorsProcessor()
                        .processRequest(source.getCorsConfiguration(request), request, response);

        assertThat(handled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(FRONT_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .contains("Authorization")
                .contains("Content-Type");
    }

    @Test
    @DisplayName("허용되지 않은 origin의 preflight는 거부한다")
    void corsPreflight_rejectsDisallowedOrigin() throws Exception {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request =
                new MockHttpServletRequest("OPTIONS", "/api/v1/auth/reissue");
        request.addHeader(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean handled =
                new DefaultCorsProcessor()
                        .processRequest(source.getCorsConfiguration(request), request, response);

        assertThat(handled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    private CorsConfiguration corsConfiguration() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        return securityConfig.corsConfigurationSource().getCorsConfiguration(request);
    }
}
