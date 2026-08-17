package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴대폰 인증 결과를 비밀번호 재설정 권한으로 바꾸는 트랜잭션.
 *
 * <p>Lock order는 PhoneVerification → User → PasswordResetToken이다. 계정 유무를 확인한 정상 시도는 결과를 그대로 반환하고
 * 커밋해, 복구 불가 계정에서도 휴대폰 인증이 소비되게 한다. 예상하지 못한 실패는 예외로 전파해 인증 소비까지 롤백시킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetAuthorityTransactionService {

    static final int TOKEN_VALIDITY_MINUTES = 10;

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AccountLoginTypeResolver accountLoginTypeResolver;
    private final PasswordResetTokenCodec passwordResetTokenCodec;
    private final Clock clock;

    @Transactional
    public PasswordResetAuthorityResult issueAuthority(UUID phoneVerificationId, String rawToken) {
        PhoneVerification verification = lockVerification(phoneVerificationId);
        LocalDateTime now = LocalDateTime.now(clock);
        validateForPasswordReset(verification, now);

        Optional<User> user =
                userRepository.findByPhoneNumberForUpdate(verification.getPhoneNumber());
        PasswordResetAuthorityResult result = issueForLockedUser(user, rawToken, now);
        // 계정 유무를 확인한 정상 시도는 결과 열거와 인증 재사용을 막기 위해 모두 소비한다.
        verification.consume(now);
        return result;
    }

    private PhoneVerification lockVerification(UUID phoneVerificationId) {
        if (phoneVerificationId == null) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        return phoneVerificationRepository
                .findByVerificationIdForUpdate(phoneVerificationId.toString())
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_VERIFICATION_NOT_FOUND));
    }

    private void validateForPasswordReset(PhoneVerification verification, LocalDateTime now) {
        if (verification.getPurpose() != PhoneVerificationPurpose.RESET_PASSWORD) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_PURPOSE_MISMATCH);
        }
        if (!verification.isVerified()) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        if (verification.isVerifiedResultExpired(
                now, PhoneVerificationRequirementService.VERIFIED_RESULT_VALIDITY_MINUTES)) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_EXPIRED);
        }
        if (verification.isConsumed()) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
    }

    private PasswordResetAuthorityResult issueForLockedUser(
            Optional<User> optionalUser, String rawToken, LocalDateTime now) {
        if (optionalUser.isEmpty()) {
            return PasswordResetAuthorityResult.accountNotFound();
        }
        User user = optionalUser.get();
        Optional<AccountLoginType> loginType = accountLoginTypeResolver.resolve(user);
        if (loginType.isEmpty()) {
            if (user.getStatus() == UserStatus.ACTIVE) {
                log.error("비밀번호 재설정 중 로그인 credential 불일치를 감지했습니다: userId={}", user.getId());
            }
            return PasswordResetAuthorityResult.accountNotFound();
        }
        return switch (loginType.get()) {
            case EMAIL -> PasswordResetAuthorityResult.email(issueToken(user, rawToken, now));
            case KAKAO -> PasswordResetAuthorityResult.kakao();
        };
    }

    private String issueToken(User user, String rawToken, LocalDateTime now) {
        String tokenHash = passwordResetTokenCodec.validateAndHash(rawToken);
        // 재발급은 직전 토큰을 즉시 무효화한다. user_id UNIQUE 충돌 없이 insert하려면 삭제가 먼저다.
        passwordResetTokenRepository.deleteAllByUserId(user.getId());
        // token hash unique 충돌을 커밋까지 미루지 않고 이 자리에서 확인해, 상위에서 재발급을 재시도할 수 있게 한다.
        passwordResetTokenRepository.saveAndFlush(
                PasswordResetToken.issue(
                        user, tokenHash, now.plusMinutes(TOKEN_VALIDITY_MINUTES), now));
        return rawToken;
    }
}
