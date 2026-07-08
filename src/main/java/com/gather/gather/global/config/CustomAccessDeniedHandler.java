package com.gather.gather.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 인가 실패(403, 인증은 됐으나 role이 부족한 경우) 응답을 공통 {@link ApiResponse} 포맷의 JSON으로 직접 작성한다.
 *
 * <p>{@link CustomAuthenticationEntryPoint}(401)와 동일한 작성 방식이며, {@link ErrorCode#FORBIDDEN} 고정으로
 * 응답한다.
 */
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(), ApiResponse.error(errorCode.name(), errorCode.getMessage()));
    }
}
