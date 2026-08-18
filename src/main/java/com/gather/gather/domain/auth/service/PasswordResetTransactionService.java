package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경과 세션 정리를 한 트랜잭션으로 처리한다.
 *
 * <p>Lock order는 User → PasswordResetToken으로, 재발급·탈퇴 흐름과 동일하다. 비밀번호 변경, 재설정 토큰 삭제, Refresh Token
 * 삭제는 함께 커밋되거나 함께 롤백되어야 한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetTransactionService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountLoginTypeResolver accountLoginTypeResolver;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public void resetPassword(Long userId, String tokenHash, String rawPassword) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(this::invalidToken);
        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByTokenHashForUpdate(tokenHash)
                        .orElseThrow(this::invalidToken);
        if (!Objects.equals(token.getUser().getId(), user.getId())) {
            throw invalidToken();
        }
        // 계정 상태·credential 상세를 응답으로 노출하지 않기 위해, 발급 이후 상태가 바뀐 경우도 INVALID로 응답한다.
        Optional<AccountLoginType> loginType = accountLoginTypeResolver.resolve(user);
        if (loginType.isEmpty() || loginType.get() != AccountLoginType.EMAIL) {
            throw invalidToken();
        }
        if (token.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
        }

        user.changePassword(passwordEncoder.encode(rawPassword));
        passwordResetTokenRepository.deleteAllByUserId(user.getId());
        // 옛 비밀번호로 만들어진 세션이 남지 않도록 해당 사용자의 Refresh Token을 모두 폐기한다.
        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }
}
