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
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int EMAIL_VERIFICATION_CODE_BOUND = 1_000_000;
    private static final int EMAIL_VERIFICATION_CODE_MIN_DIGITS = 6;
    private static final int EMAIL_VERIFICATION_EXPIRATION_MINUTES = 10;

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final TokenProvider tokenProvider;
    private final TokenIssuer tokenIssuer;
    private final SignupValidator signupValidator;
    private final LoginPolicy loginPolicy;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public EmailVerificationSendResponse sendEmailVerificationCode(
            EmailVerificationSendRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String code = generateVerificationCode();
        LocalDateTime expiresAt =
                LocalDateTime.now().plusMinutes(EMAIL_VERIFICATION_EXPIRATION_MINUTES);
        EmailVerification emailVerification =
                emailVerificationRepository
                        .findByEmail(email)
                        .orElseGet(() -> EmailVerification.create(email, code, expiresAt));
        emailVerification.refresh(code, expiresAt);
        emailVerificationRepository.save(emailVerification);

        try {
            emailSender.sendVerificationCode(email, code);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }

        return new EmailVerificationSendResponse(email, expiresAt, "인증 코드가 발송되었습니다.");
    }

    @Transactional
    public EmailVerificationConfirmResponse confirmEmailVerificationCode(
            EmailVerificationConfirmRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerification emailVerification =
                emailVerificationRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (emailVerification.isExpired(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }
        if (!emailVerification.getCode().equals(request.code())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        emailVerification.verify(now);
        return new EmailVerificationConfirmResponse(email, true, now);
    }

    @Transactional(readOnly = true)
    public PhoneNumberAvailabilityResponse checkPhoneNumberAvailability(
            PhoneNumberAvailabilityRequest request) {
        String phoneNumber = signupValidator.normalizePhoneNumber(request.phoneNumber());
        return new PhoneNumberAvailabilityResponse(
                phoneNumber, !userRepository.existsByPhoneNumber(phoneNumber));
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateSignupRequest(request);

        String email = normalizeEmail(request.email());
        String phoneNumber = signupValidator.normalizePhoneNumber(request.phoneNumber());
        String nickname = request.nickname();
        String introduction = signupValidator.normalizeNullableText(request.introduction());

        validateEmailVerified(email);
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

        try {
            return SignupResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(
                    exception, email, phoneNumber, nickname);
        }
    }

    @Transactional
    public TokenIssueResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        loginPolicy.validateLoginAllowed(user);
        return tokenIssuer.issue(user);
    }

    @Transactional
    public TokenIssueResult reissue(String rawRefreshToken) {
        RefreshToken refreshToken = findRefreshToken(rawRefreshToken);
        LocalDateTime now = LocalDateTime.now();

        if (refreshToken.isRevoked()) {
            throw new BusinessException(ErrorCode.REVOKED_TOKEN);
        }
        if (refreshToken.isExpired(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        loginPolicy.validateLoginAllowed(refreshToken.getUser());
        refreshToken.revoke(now);
        return tokenIssuer.issue(refreshToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken refreshToken = findRefreshToken(rawRefreshToken);
        if (refreshToken.isRevoked() || refreshToken.isExpired(LocalDateTime.now())) {
            return;
        }
        refreshToken.revoke(LocalDateTime.now());
    }

    private RefreshToken findRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String tokenHash = tokenProvider.hashToken(rawRefreshToken);
        return refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateVerificationCode() {
        return String.format(
                "%0" + EMAIL_VERIFICATION_CODE_MIN_DIGITS + "d",
                secureRandom.nextInt(EMAIL_VERIFICATION_CODE_BOUND));
    }
}
