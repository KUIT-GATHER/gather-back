package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.PasswordResetAuthorityRequest;
import com.gather.gather.domain.auth.dto.PasswordResetRequest;
import com.gather.gather.domain.auth.dto.PasswordResetTokenResponse;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 재설정 유스케이스의 트랜잭션 밖 조립 계층.
 *
 * <p>내부 트랜잭션이 커밋한 결과를 API 응답 또는 BusinessException으로 바꾼다. 복구 불가 계정의 예외를 트랜잭션 안에서 던지면 휴대폰 인증 소비까지
 * 롤백되므로 변환은 커밋 이후에 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_ISSUE_MAX_ATTEMPTS = 3;

    private final PasswordResetAuthorityTransactionService authorityTransactionService;
    private final PasswordResetTransactionService resetTransactionService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenCodec passwordResetTokenCodec;
    private final PasswordResetTokenConstraintResolver constraintResolver;
    private final PasswordPolicy passwordPolicy;

    public PasswordResetTokenResponse issueToken(PasswordResetAuthorityRequest request) {
        PasswordResetAuthorityResult result =
                issueAuthorityWithRetry(request.phoneVerificationId());
        return switch (result.outcome()) {
            case EMAIL -> new PasswordResetTokenResponse(result.rawToken(), result.expiresAt());
            case KAKAO -> throw new BusinessException(ErrorCode.PASSWORD_RESET_NOT_AVAILABLE);
            case ACCOUNT_NOT_FOUND -> throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        };
    }

    public void resetPassword(PasswordResetRequest request) {
        passwordPolicy.validate(request.password(), request.passwordConfirm());
        String tokenHash = passwordResetTokenCodec.validateAndHash(request.passwordResetToken());
        Long userId =
                passwordResetTokenRepository
                        .findUserIdByTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        resetTransactionService.resetPassword(userId, tokenHash, request.password());
    }

    /** token hash unique 충돌은 새 raw token으로 발급 트랜잭션 전체를 다시 시도한다. */
    private PasswordResetAuthorityResult issueAuthorityWithRetry(UUID phoneVerificationId) {
        DataIntegrityViolationException lastConflict = null;
        for (int attempt = 1; attempt <= TOKEN_ISSUE_MAX_ATTEMPTS; attempt++) {
            try {
                return authorityTransactionService.issueAuthority(
                        phoneVerificationId, passwordResetTokenCodec.generateToken());
            } catch (DataIntegrityViolationException exception) {
                if (!constraintResolver.isTokenHashConflict(exception)) {
                    throw exception;
                }
                lastConflict = exception;
                if (attempt < TOKEN_ISSUE_MAX_ATTEMPTS) {
                    log.warn(
                            "비밀번호 재설정 토큰 hash 충돌로 발급을 재시도합니다. attempt={}, maxAttempts={}",
                            attempt,
                            TOKEN_ISSUE_MAX_ATTEMPTS);
                }
            }
        }
        log.error("비밀번호 재설정 토큰 hash 충돌 재시도 횟수를 소진했습니다. maxAttempts={}", TOKEN_ISSUE_MAX_ATTEMPTS);
        throw new IllegalStateException("비밀번호 재설정 토큰 발급 재시도 횟수를 초과했습니다.", lastConflict);
    }
}
