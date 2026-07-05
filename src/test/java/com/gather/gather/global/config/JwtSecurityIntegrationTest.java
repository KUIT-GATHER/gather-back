package com.gather.gather.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserRole;
import com.gather.gather.domain.auth.service.TokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig + JwtAuthenticationFilter + CustomAuthenticationEntryPoint 통합 검증.
 *
 * <p>보호 경로로는 posting/sync 대신 테스트 전용 {@code /test/secured} ({@link
 * com.gather.gather.support.TestSecuredController})를 사용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest {

    private static final String SECURED_PATH = "/test/secured";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private JwtProperties jwtProperties;

    @Test
    @DisplayName("보호 API에 토큰 없이 요청하면 401 UNAUTHORIZED이다")
    void protectedWithoutToken_returns401Unauthorized() throws Exception {
        mockMvc.perform(get(SECURED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("만료된 토큰이면 401 EXPIRED_TOKEN이다")
    void protectedWithExpiredToken_returns401Expired() throws Exception {
        Date past = new Date(System.currentTimeMillis() - 60_000L);
        String expired =
                Jwts.builder()
                        .subject("100")
                        .claim("role", "USER")
                        .issuedAt(new Date(past.getTime() - 60_000L))
                        .expiration(past)
                        .signWith(signingKey(), Jwts.SIG.HS256)
                        .compact();

        mockMvc.perform(get(SECURED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("EXPIRED_TOKEN"));
    }

    @Test
    @DisplayName("변조된 토큰이면 401 INVALID_TOKEN이다")
    void protectedWithTamperedToken_returns401Invalid() throws Exception {
        String token = tokenProvider.createAccessToken(newUser(100L, UserRole.USER));
        // 서명의 첫 글자를 변조한다. Base64 마지막 글자는 버려지는 비트 때문에 바꿔도 서명이 유효할 수 있음
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        String tampered = String.join(".", parts);

        mockMvc.perform(get(SECURED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("permitAll 경로(/health)는 토큰 없이 통과한다")
    void permitAllPath_passesWithoutToken() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    @DisplayName("유효한 토큰이면 통과하고 principal은 Long userId, authority는 ROLE_USER이다")
    void validToken_authenticatesWithLongPrincipalAndRoleAuthority() throws Exception {
        String token = tokenProvider.createAccessToken(newUser(100L, UserRole.USER));

        mockMvc.perform(get(SECURED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.principal").value(100))
                .andExpect(jsonPath("$.data.principalType").value("Long"))
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_USER"));
    }

    @Test
    @DisplayName("유효한 토큰으로 존재하지 않는 경로를 요청하면 404 NOT_FOUND이다")
    void validTokenUnknownPath_returns404NotFound() throws Exception {
        String token = tokenProvider.createAccessToken(newUser(100L, UserRole.USER));

        mockMvc.perform(get("/no-such-path").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Bearer가 아닌 Authorization 헤더(Basic)는 JWT 인증 시도로 보지 않아 401 UNAUTHORIZED이다")
    void nonBearerHeader_returns401Unauthorized() throws Exception {
        mockMvc.perform(get(SECURED_PATH).header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
    }

    private static User newUser(Long id, UserRole role) {
        try {
            var constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            User user = constructor.newInstance();
            ReflectionTestUtils.setField(user, "id", id);
            ReflectionTestUtils.setField(user, "role", role);
            return user;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
