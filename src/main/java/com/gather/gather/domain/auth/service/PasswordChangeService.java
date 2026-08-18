package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.dto.PasswordChangeRequest;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 상태에서의 비밀번호 변경을 한 트랜잭션으로 처리한다.
 *
 * <p>Lock order는 User → PasswordResetToken → RefreshToken으로 로그인·재발급·재설정·탈퇴 흐름과 동일하다. 비밀번호 변경, 재설정
 * 토큰 삭제, Refresh Token 삭제는 함께 커밋되거나 함께 롤백되어야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountLoginTypeResolver accountLoginTypeResolver;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        // 정책을 위반한 요청이 User 잠금을 잡지 않도록 조회 전에 검증한다.
        passwordPolicy.validate(request.password(), request.passwordConfirm());

        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateChangeableStatus(user);
        validateEmailCredential(user);

        // 잠금 이후의 최신 hash로 검증해야 재설정·변경이 먼저 커밋된 경우를 놓치지 않는다.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        user.changePassword(passwordEncoder.encode(request.password()));
        // 발급된 재설정 토큰이 남으면 옛 recovery credential로 새 비밀번호를 다시 덮어쓸 수 있다.
        passwordResetTokenRepository.deleteAllByUserId(userId);
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    private void validateChangeableStatus(User user) {
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

    private void validateEmailCredential(User user) {
        Optional<AccountLoginType> loginType = accountLoginTypeResolver.resolveCredentialType(user);
        if (loginType.isEmpty()) {
            log.error("비밀번호 변경 중 로그인 credential 불일치를 감지했습니다: userId={}", user.getId());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        // KAKAO 전용 계정에 password credential이 새로 생기지 않도록 변경 자체를 막는다.
        if (loginType.get() != AccountLoginType.EMAIL) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_NOT_AVAILABLE);
        }
    }
}
