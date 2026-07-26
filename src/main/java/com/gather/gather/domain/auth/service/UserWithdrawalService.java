package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 처리.
 *
 * <p>PII 익명화·재가입 허용 정책·카카오 unlink는 별도 합의가 끝나지 않아 이 클래스 범위 밖이다(추후 확장 지점). 다른 도메인의 부수 정리는 {@link
 * UserWithdrawnEvent}를 발행해 각 도메인 리스너가 같은 트랜잭션 안에서 수행하도록 위임한다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void withdraw() {
        Long userId = SecurityUtil.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.withdraw();
        refreshTokenRepository.deleteByUser_Id(userId);
        eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
    }
}
