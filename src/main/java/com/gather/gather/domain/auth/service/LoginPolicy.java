package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 로그인·토큰 발급이 허용된 계정 상태인지 검증한다. 일반 로그인, 토큰 재발급, 카카오 로그인이 토큰을 발급하기 직전 동일하게 적용한다.
 *
 * <p>인증 수단(비밀번호/refresh token/카카오 회원번호)이 달라도 정지·탈퇴 제재는 동일하게 걸려야 하므로 한 곳에 둔다.
 */
@Component
public class LoginPolicy {

    public void validateLoginAllowed(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SUSPENDED_USER);
        }
        if (user.getStatus() == UserStatus.WITHDRAWAL_PENDING) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_PENDING_USER);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
    }
}
