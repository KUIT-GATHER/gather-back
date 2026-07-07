package com.gather.gather.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.auth.service.TokenProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 인증 없이 접근 가능한 경로. 그 외 모든 요청은 Access Token 인증이 필요하다.
    // 참고: /api/v1/postings/sync는 의도적으로 인증 대상(팀 결정)이라 여기에 넣지 않는다.
    // GET 전용 공개 조회 경로. 문자열 매처는 HTTP 메서드를 구분하지 않으므로, 같은 경로에
    // 쓰기 요청(POST 등)이 나중에 추가돼도 함께 열리지 않도록 GET으로 한정해 등록한다.
    private static final String[] PERMIT_ALL_GET_PATHS = {"/api/v1/postings", "/api/v1/regions"};

    private static final String[] PERMIT_ALL_PATHS = {
        "/health",
        "/api/v1/auth/**",
        "/api/v1/categories",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/",
        "/v3/api-docs/**"
    };

    private final TokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(HttpMethod.GET, PERMIT_ALL_GET_PATHS)
                                        .permitAll()
                                        .requestMatchers(PERMIT_ALL_PATHS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(tokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        exception ->
                                exception.authenticationEntryPoint(
                                        new CustomAuthenticationEntryPoint(objectMapper)))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();
        // 프론트 배포 주소가 확정되기 전까지 로컬 연동 테스트를 우선하기 위해 임시 허용
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
