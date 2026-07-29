package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 종료 코어. 탈퇴 API와 카카오 연결 해제 웹훅이 공유하며, 두 경로의 차이는 카카오 unlink 호출 유무뿐이라 여기서 갈라지지 않는다.
 *
 * <p>카카오 unlink처럼 실패할 수 있는 외부 호출은 이 트랜잭션 밖에서 수행한다. 카카오 장애로 탈퇴 자체가 실패하면 안 되기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTerminationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 이미 WITHDRAWN이면 아무것도 하지 않는다. Access Token이 최대 30분 잔존해 탈퇴 API가 다시 호출될 수 있고, 웹훅도 중복 수신될 수 있어 두
     * 경로 모두 멱등해야 한다.
     *
     * <p>상태 검사와 갱신 사이를 비관적 락으로 막는다. 락이 없으면 동시 요청이 둘 다 ACTIVE를 읽어 이벤트가 두 번 발행되고, 구독자의 모임장 승계 같은 처리가
     * 중복 실행된다.
     */
    @Transactional
    public void terminate(Long userId, WithdrawalReason reason) {
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            log.info("이미 탈퇴한 계정이라 종료 처리를 건너뜁니다: userId={}, reason={}", userId, reason);
            return;
        }

        String email = user.getEmail();
        user.withdraw(reason, LocalDateTime.now());
        if (email != null) {
            emailVerificationRepository.deleteByEmail(email);
        }
        refreshTokenRepository.deleteByUser(user);
        eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
    }
}
