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
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.category.repository.CategoryRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final int MAX_KOREAN_NAME_LENGTH = 7;
    private static final int MAX_NAME_LENGTH = 12;
    private static final int MIN_ACTIVITY_REGION_COUNT = 1;
    private static final int MAX_ACTIVITY_REGION_COUNT = 3;
    // 활동 지역은 시도(최상위) 단위만 선택할 수 있다. Region.level 1 = 시도.
    private static final int ACTIVITY_REGION_LEVEL = 1;
    private static final int MIN_INTEREST_CATEGORY_COUNT = 1;

    // MySQL unique 제약 위반 메시지 예: "Duplicate entry 'test@example.com' for key 'users.UK_xxx'"
    private static final Pattern DUPLICATE_ENTRY_PATTERN =
            Pattern.compile("Duplicate entry '(.+?)' for key");

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final TokenProvider tokenProvider;
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
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        return new PhoneNumberAvailabilityResponse(
                phoneNumber, !userRepository.existsByPhoneNumber(phoneNumber));
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateSignupRequest(request);

        String email = normalizeEmail(request.email());
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        String nickname = request.nickname().trim();
        String introduction = normalizeNullableText(request.introduction());

        validateEmailVerified(email);
        validateDuplicates(email, phoneNumber, nickname);

        List<Region> activityRegions = findActivityRegions(request.activityRegionIds());
        List<Category> interestCategories = findInterestCategories(request.interestCategoryIds());

        User user =
                User.create(
                        request.name().trim(),
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
                        activityRegions,
                        interestCategories);

        try {
            return SignupResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw resolveDuplicateException(exception, email, phoneNumber, nickname);
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

        validateLoginAllowed(user);
        return issueTokens(user);
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

        validateLoginAllowed(refreshToken.getUser());
        refreshToken.revoke(now);
        return issueTokens(refreshToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken refreshToken = findRefreshToken(rawRefreshToken);
        if (refreshToken.isRevoked() || refreshToken.isExpired(LocalDateTime.now())) {
            return;
        }
        refreshToken.revoke(LocalDateTime.now());
    }

    private TokenIssueResult issueTokens(User user) {
        String accessToken = tokenProvider.createAccessToken(user);
        String refreshToken = tokenProvider.generateToken();
        String refreshTokenHash = tokenProvider.hashToken(refreshToken);

        refreshTokenRepository.save(
                RefreshToken.create(refreshTokenHash, user, tokenProvider.refreshTokenExpiresAt()));
        return new TokenIssueResult(accessToken, refreshToken);
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
        validateName(request.name());
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (!Boolean.TRUE.equals(request.serviceTermsAgreed())
                || !Boolean.TRUE.equals(request.privacyPolicyAgreed())) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        validateActivityRegionIds(request.activityRegionIds());
        validateInterestCategoryIds(request.interestCategoryIds());
    }

    private void validateName(String name) {
        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (trimmedName.matches("^[가-힣]+$") && trimmedName.length() > MAX_KOREAN_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateActivityRegionIds(List<Long> activityRegionIds) {
        if (activityRegionIds == null
                || activityRegionIds.size() < MIN_ACTIVITY_REGION_COUNT
                || activityRegionIds.size() > MAX_ACTIVITY_REGION_COUNT
                || hasNullId(activityRegionIds)
                || hasDuplicateIds(activityRegionIds)) {
            throw new BusinessException(ErrorCode.INVALID_ACTIVITY_REGION_COUNT);
        }
    }

    private void validateInterestCategoryIds(List<Long> interestCategoryIds) {
        if (interestCategoryIds == null
                || interestCategoryIds.size() < MIN_INTEREST_CATEGORY_COUNT
                || hasNullId(interestCategoryIds)
                || hasDuplicateIds(interestCategoryIds)) {
            throw new BusinessException(ErrorCode.INVALID_INTEREST_CATEGORY_COUNT);
        }
    }

    private boolean hasNullId(List<Long> ids) {
        return ids.stream().anyMatch(Objects::isNull);
    }

    private boolean hasDuplicateIds(List<Long> ids) {
        return new LinkedHashSet<>(ids).size() != ids.size();
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
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    // 사전 중복 검사를 통과한 동시 요청이 DB unique 제약에서 충돌한 경우, 위반된 컬럼을 식별해 409 계열
    // 에러로 변환한다. 식별할 수 없는 무결성 위반은 원본 예외를 그대로 반환해 공통 서버 오류 흐름을 유지한다.
    private RuntimeException resolveDuplicateException(
            DataIntegrityViolationException exception,
            String email,
            String phoneNumber,
            String nickname) {
        String duplicateValue = extractDuplicateValue(exception);
        if (duplicateValue == null) {
            return exception;
        }
        if (duplicateValue.equalsIgnoreCase(email)) {
            return new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (duplicateValue.equalsIgnoreCase(phoneNumber)) {
            return new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        if (duplicateValue.equalsIgnoreCase(nickname)) {
            return new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return exception;
    }

    private String extractDuplicateValue(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message == null) {
            return null;
        }
        Matcher matcher = DUPLICATE_ENTRY_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<Region> findActivityRegions(List<Long> activityRegionIds) {
        Set<Long> requestedIds = new LinkedHashSet<>(activityRegionIds);
        List<Region> regions = regionRepository.findAllById(requestedIds);
        if (regions.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
        // 활동 지역은 시도 단위만 허용한다. 프론트는 시도 목록만 노출하므로 정상 흐름에서는 발생하지 않고,
        // 시군구/읍면동 등 하위 지역 id가 전달된 비정상 요청을 걸러낸다.
        boolean allTopLevel =
                regions.stream()
                        .allMatch(
                                region -> Objects.equals(region.getLevel(), ACTIVITY_REGION_LEVEL));
        if (!allTopLevel) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
        return new ArrayList<>(regions);
    }

    private List<Category> findInterestCategories(List<Long> interestCategoryIds) {
        Set<Long> requestedIds = new LinkedHashSet<>(interestCategoryIds);
        List<Category> categories = categoryRepository.findAllById(requestedIds);
        if (categories.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return new ArrayList<>(categories);
    }

    private void validateLoginAllowed(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SUSPENDED_USER);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = phoneNumber.replaceAll("[\\s-]", "");
        if (!normalized.matches("^[0-9]+$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    private String normalizeNullableText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private String generateVerificationCode() {
        return String.format(
                "%0" + EMAIL_VERIFICATION_CODE_MIN_DIGITS + "d",
                secureRandom.nextInt(EMAIL_VERIFICATION_CODE_BOUND));
    }
}
