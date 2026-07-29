package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 일반 회원가입과 소셜 회원가입이 공유하는 입력 검증·정규화.
 *
 * <p>이메일·비밀번호 검증은 일반 회원가입 전용이라 여기 두지 않는다({@link AuthService}가 담당).
 */
@Component
@RequiredArgsConstructor
public class SignupValidator {

    private static final Pattern KOREAN_OR_ENGLISH_PATTERN =
            Pattern.compile("^(?:[가-힣]{2,10}|[A-Za-z]{2,20})$");
    // 활동 지역은 시군구 단위만 선택할 수 있다. Region.level 2 = 시군구.
    private static final int ACTIVITY_REGION_LEVEL = 2;
    private static final int MIN_INTEREST_CATEGORY_COUNT = 1;

    // MySQL unique 제약 위반 메시지 예: "Duplicate entry 'test@example.com' for key 'users.UK_xxx'"
    private static final Pattern DUPLICATE_ENTRY_PATTERN =
            Pattern.compile("Duplicate entry '(.+?)' for key");

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public void validateName(String name) {
        validateKoreanOrEnglish(name);
    }

    public void validateNickname(String nickname) {
        validateKoreanOrEnglish(nickname);
    }

    public void validateRequiredTermsAgreed(
            Boolean serviceTermsAgreed, Boolean privacyPolicyAgreed) {
        if (!Boolean.TRUE.equals(serviceTermsAgreed) || !Boolean.TRUE.equals(privacyPolicyAgreed)) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
    }

    public void validateActivityRegionId(Long activityRegionId) {
        if (activityRegionId == null) {
            throw new BusinessException(ErrorCode.INVALID_ACTIVITY_REGION);
        }
    }

    public void validateInterestCategories(List<PostingCategory> interestCategories) {
        if (interestCategories == null
                || interestCategories.size() < MIN_INTEREST_CATEGORY_COUNT
                || hasNullCategory(interestCategories)
                || hasDuplicateCategories(interestCategories)) {
            throw new BusinessException(ErrorCode.INVALID_INTEREST_CATEGORY_COUNT);
        }
    }

    public void validatePhoneNumberNotDuplicated(String phoneNumber) {
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER);
        }
    }

    public void validateNicknameNotDuplicated(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    /** 마이페이지 프로필 수정처럼 본인 닉네임은 검사 대상에서 제외해야 하는 경우용. */
    public void validateNicknameNotDuplicated(String nickname, Long excludeUserId) {
        if (userRepository.existsByNicknameAndIdNot(nickname, excludeUserId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    public Region findActivityRegion(Long activityRegionId) {
        Region region =
                regionRepository
                        .findById(activityRegionId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
        if (!Objects.equals(region.getLevel(), ACTIVITY_REGION_LEVEL)) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
        return region;
    }

    public String normalizePhoneNumber(String phoneNumber) {
        return phoneNumberNormalizer.normalize(phoneNumber);
    }

    public String normalizeNullableText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    /**
     * 사전 중복 검사를 통과한 동시 요청이 DB unique 제약에서 충돌한 경우, 위반된 컬럼을 식별해 409 계열 에러로 변환한다. 식별할 수 없는 무결성 위반은 원본
     * 예외를 그대로 반환해 공통 서버 오류 흐름을 유지한다.
     *
     * <p>email은 이메일을 쓰지 않는 소셜 가입에서 null로 넘어온다.
     */
    public RuntimeException resolveDuplicateException(
            DataIntegrityViolationException exception,
            String email,
            String phoneNumber,
            String nickname) {
        String duplicateValue = extractDuplicateValue(exception);
        if (duplicateValue == null) {
            return exception;
        }
        if (email != null && duplicateValue.equalsIgnoreCase(email)) {
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

    private void validateKoreanOrEnglish(String value) {
        if (value == null || !KOREAN_OR_ENGLISH_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean hasNullCategory(List<PostingCategory> categories) {
        return categories.stream().anyMatch(Objects::isNull);
    }

    private boolean hasDuplicateCategories(List<PostingCategory> categories) {
        return new LinkedHashSet<>(categories).size() != categories.size();
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
}
