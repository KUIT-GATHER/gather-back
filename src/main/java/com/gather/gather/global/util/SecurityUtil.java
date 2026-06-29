package com.gather.gather.global.util;

/**
 * 현재 로그인한 사용자 ID 꺼내는 유틸.
 *
 * ⚠️ 임시 버전: JWT 인증이 완성되기 전까지 항상 '1L'을 반환
 *
 * [사용법]
 *   컨트롤러/서비스에서 로그인 사용자 id가 필요하면:
 *       Long userId = SecurityUtil.getCurrentUserId();
 *   예) 모임 생성 host_id, 게시글 작성 user_id 등에 사용.
 *
 * 나중에 JWT가 완성되면 이 메서드 "내부만" 토큰에서 꺼내도록 바꾸기!
 * 호출하는 쪽 코드는 한 줄도 안 바뀝니다
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long getCurrentUserId() {
        // TODO(인증 완성 후 교체):
        //   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //   return ((CustomUserDetails) auth.getPrincipal()).getUserId();
        return 1L; // 임시: 항상 1번 사용자라고 가정
    }
}