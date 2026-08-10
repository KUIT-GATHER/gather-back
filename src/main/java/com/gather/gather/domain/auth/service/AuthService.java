package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.EmailVerificationSendRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationSendResponse;
import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.PhoneNumberAvailabilityRequest;
import com.gather.gather.domain.auth.dto.PhoneNumberAvailabilityResponse;
import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.dto.SignupResponse;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int EMAIL_VERIFICATION_CODE_BOUND = 1_000_000;
    private static final int EMAIL_VERIFICATION_CODE_MIN_DIGITS = 6;
    private static final int EMAIL_VERIFICATION_EXPIRATION_MINUTES = 10;
    private static final int EMAIL_RESEND_COOLDOWN_MINUTES = 3;
    private static final int EMAIL_DAILY_SEND_LIMIT = 5;
    private static final int EMAIL_MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int MYSQL_DUPLICATE_ENTRY_ERROR_CODE = 1062;
    private static final String EMAIL_VERIFICATION_UNIQUE_CONSTRAINT =
            "uk_email_verification_email";

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final TokenProvider tokenProvider;
    private final TokenIssuer tokenIssuer;
    private final LockedTokenIssuanceService lockedTokenIssuanceService;
    private final SignupValidator signupValidator;
    private final LoginPolicy loginPolicy;
    private final AccountRejoinBlockService accountRejoinBlockService;
    private final AccountIdentityGuardService accountIdentityGuardService;
    private final PhoneVerificationRequirementService phoneVerificationRequirementService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public EmailVerificationSendResponse sendEmailVerificationCode(
            EmailVerificationSendRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        LocalDateTime now = LocalDateTime.now();
        String code = generateVerificationCode();
        LocalDateTime expiresAt = now.plusMinutes(EMAIL_VERIFICATION_EXPIRATION_MINUTES);

        EmailVerification emailVerification = null;
        // 없는 행을 FOR UPDATE로 조회하면 MySQL gap lock으로 최초 발송끼리 데드락이 날 수 있어,
        // 기존 행이 확인된 경우에만 비관적 잠금을 건다.
        if (emailVerificationRepository.existsByEmail(email)) {
            emailVerification =
                    emailVerificationRepository.findByEmailForUpdate(email).orElse(null);
        }
        EmailVerificationState previousState = null;
        if (emailVerification == null) {
            emailVerification = EmailVerification.create(email, code, expiresAt);
            try {
                emailVerificationRepository.saveAndFlush(emailVerification);
            } catch (DataIntegrityViolationException exception) {
                if (isEmailVerificationUniqueConflict(exception)) {
                    throw new BusinessException(ErrorCode.EMAIL_RESEND_TOO_SOON);
                }
                throw exception;
            }
        } else {
            if (emailVerification.isWithinResendCooldown(now, EMAIL_RESEND_COOLDOWN_MINUTES)) {
                throw new BusinessException(ErrorCode.EMAIL_RESEND_TOO_SOON);
            }
            if (emailVerification.dailySendCountAsOf(now.toLocalDate()) >= EMAIL_DAILY_SEND_LIMIT) {
                throw new BusinessException(ErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
            }
            previousState = EmailVerificationState.from(emailVerification);
            emailVerification.refresh(code, expiresAt);
            emailVerificationRepository.saveAndFlush(emailVerification);
        }

        // 메일은 커밋 이후에 보내고, 실패하면 해당 발송 세대만 조건부 삭제 또는 직전 상태로 복구한다.
        FailedEmailDeliveryCompensation compensation =
                new FailedEmailDeliveryCompensation(
                        emailVerification.getId(), emailVerification.getVersion(), previousState);
        scheduleVerificationEmail(email, code, compensation);

        // 다음 재발송 가능 시각은 저장된 createdAt 기준으로 계산해, 안내 시각과 실제 쿨다운 판정을 일치시킨다.
        LocalDateTime resendAvailableAt =
                emailVerification.getCreatedAt().plusMinutes(EMAIL_RESEND_COOLDOWN_MINUTES);
        return new EmailVerificationSendResponse(
                email, expiresAt, resendAvailableAt, "인증 코드가 발송되었습니다.");
    }

    // 카운터를 증가시킨 뒤 던지는 오답 예외만 롤백하지 않아, 다른 BusinessException의 롤백 의미를 보존한다.
    @Transactional(noRollbackFor = EmailVerificationAttemptFailureException.class)
    public EmailVerificationConfirmResponse confirmEmailVerificationCode(
            EmailVerificationConfirmRequest request) {
        String email = normalizeEmail(request.email());
        // 없는 행을 FOR UPDATE로 조회하면 unique 인덱스의 빈 갭에 gap lock이 걸려, 같은 갭에 INSERT하려는
        // 동시 발송 요청과 충돌한다. 인증 없이 호출 가능한 공개 엔드포인트이므로 send와 같은 선확인을 둔다.
        if (!emailVerificationRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND);
        }
        EmailVerification emailVerification =
                emailVerificationRepository
                        .findByEmailForUpdate(email)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (emailVerification.isExpired(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }
        if (emailVerification.isAttemptExceeded(EMAIL_MAX_VERIFICATION_ATTEMPTS)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!emailVerification.getCode().equals(request.code())) {
            emailVerification.increaseAttempt();
            if (emailVerification.isAttemptExceeded(EMAIL_MAX_VERIFICATION_ATTEMPTS)) {
                throw new EmailVerificationAttemptFailureException(
                        ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
            }
            throw new EmailVerificationAttemptFailureException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        emailVerification.verify(now);
        return new EmailVerificationConfirmResponse(email, true, now);
    }

    @Transactional(readOnly = true)
    public PhoneNumberAvailabilityResponse checkPhoneNumberAvailability(
            PhoneNumberAvailabilityRequest request) {
        String phoneNumber = signupValidator.normalizePhoneNumber(request.phoneNumber());
        LocalDateTime now = LocalDateTime.now(clock);
        return new PhoneNumberAvailabilityResponse(
                phoneNumber,
                !accountRejoinBlockService.isPhoneBlocked(phoneNumber, now)
                        && !userRepository.existsByPhoneNumber(phoneNumber));
    }

    @Transactional
    public SignupResult signup(SignupRequest request) {
        validateSignupRequest(request);

        String email = normalizeEmail(request.email());
        String phoneNumber = signupValidator.normalizePhoneNumber(request.phoneNumber());
        String nickname = request.nickname();
        String introduction = signupValidator.normalizeNullableText(request.introduction());
        LocalDateTime now = LocalDateTime.now(clock);

        RejoinBlockIdentifier phoneIdentifier =
                accountIdentityGuardService.lockPhone(phoneNumber, now);
        validatePhoneRejoinAllowed(phoneIdentifier, now);
        validateEmailVerified(email);
        phoneVerificationRequirementService.consumeForSignup(
                request.phoneVerificationId(), phoneNumber);
        validateDuplicates(email, phoneNumber, nickname);

        Region activityRegion = signupValidator.findActivityRegion(request.activityRegionId());

        User user =
                User.create(
                        request.name(),
                        request.birthDate(),
                        request.gender(),
                        phoneNumber,
                        email,
                        passwordEncoder.encode(request.password()),
                        nickname,
                        introduction,
                        request.serviceTermsAgreed(),
                        request.privacyPolicyAgreed(),
                        request.marketingAgreed(),
                        activityRegion,
                        request.interestCategories());

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(
                    exception, email, phoneNumber, nickname);
        }

        TokenIssueResult tokens = tokenIssuer.issue(savedUser);
        return new SignupResult(
                SignupResponse.bearer(savedUser, tokens.accessToken()), tokens.refreshToken());
    }

    public TokenIssueResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        return lockedTokenIssuanceService.issue(user.getId());
    }

    @Transactional
    public TokenIssueResult reissue(String rawRefreshToken) {
        String tokenHash = requireRefreshTokenHash(rawRefreshToken);
        Long userId =
                refreshTokenRepository
                        .findUserIdByTokenHash(tokenHash)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        RefreshToken refreshToken = findRefreshTokenForUpdate(tokenHash);
        LocalDateTime now = LocalDateTime.now(clock);

        if (!java.util.Objects.equals(refreshToken.getUser().getId(), user.getId())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (refreshToken.isRevoked()) {
            throw new BusinessException(ErrorCode.REVOKED_TOKEN);
        }
        if (refreshToken.isExpired(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        loginPolicy.validateLoginAllowed(user);
        refreshToken.revoke(now);
        return tokenIssuer.issue(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken refreshToken =
                findRefreshTokenForUpdate(requireRefreshTokenHash(rawRefreshToken));
        LocalDateTime now = LocalDateTime.now(clock);
        if (refreshToken.isRevoked() || refreshToken.isExpired(now)) {
            return;
        }
        refreshToken.revoke(now);
    }

    private RefreshToken findRefreshTokenForUpdate(String tokenHash) {
        return refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

    private String requireRefreshTokenHash(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return tokenProvider.hashToken(rawRefreshToken);
    }

    private void validateSignupRequest(SignupRequest request) {
        signupValidator.validateName(request.name());
        signupValidator.validateNickname(request.nickname());
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        signupValidator.validateRequiredTermsAgreed(
                request.serviceTermsAgreed(), request.privacyPolicyAgreed());
        signupValidator.validateActivityRegionId(request.activityRegionId());
        signupValidator.validateInterestCategories(request.interestCategories());
    }

    private void validateEmailVerified(String email) {
        EmailVerification emailVerification =
                emailVerificationRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));
        if (!emailVerification.isVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    private void validateDuplicates(String email, String phoneNumber, String nickname) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        signupValidator.validatePhoneNumberNotDuplicated(phoneNumber);
        signupValidator.validateNicknameNotDuplicated(nickname);
    }

    private void validatePhoneRejoinAllowed(
            RejoinBlockIdentifier phoneIdentifier, LocalDateTime now) {
        if (accountRejoinBlockService.isBlockedForUpdate(phoneIdentifier, now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_REJOIN_BLOCKED);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private boolean isEmailVerificationUniqueConflict(DataIntegrityViolationException exception) {
        String constraintName = findConstraintName(exception);
        if (constraintName != null) {
            return isEmailVerificationUniqueConstraint(constraintName);
        }
        if (hasMySqlDuplicateEntryError(exception)) {
            return true;
        }
        return hasEmailVerificationConstraintInMessage(exception);
    }

    private String findConstraintName(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && constraintViolationException.getConstraintName() != null) {
                return constraintViolationException.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private boolean isEmailVerificationUniqueConstraint(String constraintName) {
        String normalizedConstraintName =
                constraintName.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
        return normalizedConstraintName.equals(EMAIL_VERIFICATION_UNIQUE_CONSTRAINT)
                || normalizedConstraintName.endsWith("." + EMAIL_VERIFICATION_UNIQUE_CONSTRAINT);
    }

    // 제약 이름을 얻지 못했을 때의 차선책. email_verification의 unique 제약이 email 하나뿐이라는 전제에
    // 기대므로, 이 테이블에 unique 컬럼을 추가하면 무관한 중복키까지 이메일 충돌로 오분류된다.
    private boolean hasMySqlDuplicateEntryError(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY_ERROR_CODE) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean hasEmailVerificationConstraintInMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT)
                            .contains(EMAIL_VERIFICATION_UNIQUE_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void scheduleVerificationEmail(
            String email, String code, FailedEmailDeliveryCompensation compensation) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendVerificationEmail(email, code, compensation);
                        }
                    });
        } else {
            sendVerificationEmail(email, code, compensation);
        }
    }

    private void sendVerificationEmail(
            String email, String code, FailedEmailDeliveryCompensation compensation) {
        try {
            emailSender.sendVerificationCode(email, code);
        } catch (RuntimeException exception) {
            log.error("이메일 인증 코드 발송 실패: email={}", maskEmail(email), exception);
            compensateFailedEmailDelivery(email, compensation);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED, exception);
        }
    }

    private void compensateFailedEmailDelivery(
            String email, FailedEmailDeliveryCompensation compensation) {
        try {
            int affectedRows;
            if (compensation.previousState() == null) {
                affectedRows =
                        emailVerificationRepository.deleteByIdAndVersion(
                                compensation.id(), compensation.failedVersion());
            } else {
                EmailVerificationState previous = compensation.previousState();
                affectedRows =
                        emailVerificationRepository.restoreAfterFailedResend(
                                compensation.id(),
                                compensation.failedVersion(),
                                previous.code(),
                                previous.verified(),
                                previous.expiresAt(),
                                previous.verifiedAt(),
                                previous.createdAt(),
                                previous.dailySendCount(),
                                previous.attemptCount());
            }
            if (affectedRows == 0) {
                log.warn("이메일 발송 실패 보상 생략: 이후 상태 변경 감지, email={}", maskEmail(email));
            }
        } catch (RuntimeException compensationException) {
            log.error("이메일 발송 실패 보상 중 DB 오류: email={}", maskEmail(email), compensationException);
        }
    }

    private String generateVerificationCode() {
        return String.format(
                "%0" + EMAIL_VERIFICATION_CODE_MIN_DIGITS + "d",
                secureRandom.nextInt(EMAIL_VERIFICATION_CODE_BOUND));
    }

    private static final class EmailVerificationAttemptFailureException extends BusinessException {

        private EmailVerificationAttemptFailureException(ErrorCode errorCode) {
            super(errorCode);
        }
    }

    private record FailedEmailDeliveryCompensation(
            Long id, Long failedVersion, EmailVerificationState previousState) {}

    private record EmailVerificationState(
            String code,
            boolean verified,
            LocalDateTime expiresAt,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            int dailySendCount,
            int attemptCount) {

        private static EmailVerificationState from(EmailVerification emailVerification) {
            return new EmailVerificationState(
                    emailVerification.getCode(),
                    emailVerification.isVerified(),
                    emailVerification.getExpiresAt(),
                    emailVerification.getVerifiedAt(),
                    emailVerification.getCreatedAt(),
                    emailVerification.getDailySendCount(),
                    emailVerification.getAttemptCount());
        }
    }
}
