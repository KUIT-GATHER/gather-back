package com.gather.gather.global.util;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 현재 로그인한 사용자 ID를 꺼내는 유틸.
 *
 * <p>JwtAuthenticationFilter가 SecurityContext에 저장한 principal(Long userId)을 반환한다. 인증 정보가
 * 없거나 anonymous면 {@link ErrorCode#UNAUTHORIZED}로 예외를 던진다.
 *
 * <p>[사용법] 컨트롤러/서비스에서 로그인 사용자 id가 필요하면:
 *
 * <pre>{@code
 * Long userId = SecurityUtil.getCurrentUserId();
 * }</pre>
 *
 * 예) 모임 생성 host_id, 게시글 작성 user_id 등에 사용.
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
