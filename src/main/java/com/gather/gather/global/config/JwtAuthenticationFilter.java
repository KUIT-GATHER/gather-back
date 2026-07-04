package com.gather.gather.global.config;

import com.gather.gather.domain.auth.service.AccessTokenPayload;
import com.gather.gather.domain.auth.service.JwtAuthenticationException;
import com.gather.gather.domain.auth.service.TokenProvider;
import com.gather.gather.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization 헤더의 Bearer Access Token을 검증해 SecurityContext에 인증 정보를 채우는 필터.
 *
 * <p>인증 실패(무효/만료) 시 예외를 밖으로 던지지 않고, ErrorCode를 request attribute에 심은 뒤 인증 없이 체인을 이어간다. 최종 401 응답
 * 형식은 {@link CustomAuthenticationEntryPoint}가 담당한다. 상태 저장을 피하기 위해 이 필터는 DB를 조회하지 않는다(정지/탈퇴 차단은
 * login/reissue 시점에서 처리).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 필터가 인증 실패 ErrorCode를 심는 request attribute 키. EntryPoint가 이 값을 읽는다. */
    public static final String ERROR_CODE_ATTRIBUTE = "jwt.errorCode";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    public JwtAuthenticationFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !isBearer(header)) {
            // 헤더 없음 또는 Bearer가 아닌 헤더(Basic 등)는 JWT 인증 시도가 아니므로 그대로 통과시킨다.
            // 보호 경로라면 이후 EntryPoint가 UNAUTHORIZED로 처리한다.
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AccessTokenPayload payload = tokenProvider.parseAccessToken(token);
            SecurityContextHolder.getContext().setAuthentication(toAuthentication(payload));
        } catch (JwtAuthenticationException exception) {
            request.setAttribute(ERROR_CODE_ATTRIBUTE, exception.getErrorCode());
        }
        filterChain.doFilter(request, response);
    }

    // Bearer prefix 비교는 대소문자를 무시한다(RFC 6750). substring 길이는 prefix 길이로 고정.
    private boolean isBearer(String header) {
        return header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }

    private UsernamePasswordAuthenticationToken toAuthentication(AccessTokenPayload payload) {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + payload.role().name()));
        return new UsernamePasswordAuthenticationToken(payload.userId(), null, authorities);
    }
}
