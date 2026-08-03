package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 기존 Access Token으로 보호 API에 접근할 수 있는 계정 상태인지 검증한다.
 *
 * <p>로그인 정책과 달리 기존 SUSPENDED 계정의 Access Token 접근은 유지한다. 탈퇴 상태의 멱등 요청은 JWT 검증과 User 조회를 마친 뒤 정확한
 * DELETE endpoint로 판별된 경우에만 허용한다.
 */
@Component
public class ProtectedAccessPolicy {

    public void validateAccessAllowed(User user, boolean accountTerminationRequest) {
        if (accountTerminationRequest) {
            return;
        }
        if (user.getStatus() == UserStatus.WITHDRAWAL_PENDING) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_PENDING_USER);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
    }
}
