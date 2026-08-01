package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.client.KakaoApiClient;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.dto.KakaoLoginRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoSignupRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoUserResponse;
import com.gather.gather.domain.auth.service.AccountRejoinBlockService;
import com.gather.gather.domain.auth.service.LockedTokenIssuanceService;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.SocialAccountIdentityService;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoAuthService {

    private final KakaoApiClient kakaoApiClient;
    private final KakaoProperties kakaoProperties;
    private final SocialSignupSessionService signupSessionService;
    private final RejoinBlockIdentifierHasher identifierHasher;
    private final SocialAccountProviderIdCipher providerIdCipher;
    private final SocialAccountIdentityService socialAccountIdentityService;
    private final KakaoSignupTransactionService signupTransactionService;
    private final SignupValidator signupValidator;
    private final LockedTokenIssuanceService lockedTokenIssuanceService;
    private final AccountRejoinBlockService accountRejoinBlockService;
    private final Clock clock;

    /**
     * 카카오 인증 결과로 기존 회원 로그인과 신규 회원 가입 토큰 발급을 분기한다.
     *
     * <p>카카오 API 호출 두 번이 포함돼 있어 이 메서드에는 트랜잭션을 열지 않는다. 기존 row의 lazy backfill과 Refresh Token 저장은 외부
     * 응답을 받은 뒤 각각 짧은 트랜잭션으로 처리한다.
     */
    public KakaoLoginResult login(KakaoLoginRequest request) {
        validateRedirectUri(request.redirectUri());

        String kakaoAccessToken =
                kakaoApiClient.requestAccessToken(
                        request.authorizationCode(), request.redirectUri());
        KakaoUserResponse userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
        String providerUserId = String.valueOf(userInfo.id());
        LocalDateTime now = LocalDateTime.now(clock);
        RejoinBlockIdentifier identifier = identifierHasher.hashKakao(providerUserId);

        Optional<SocialAccount> existingAccount =
                socialAccountIdentityService.findKakaoAccount(providerUserId, identifier);
        if (existingAccount.isPresent()) {
            return loginExistingMember(existingAccount.get());
        }
        if (accountRejoinBlockService.isKakaoBlocked(providerUserId, now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_REJOIN_BLOCKED);
        }
        EncryptedProviderUserId encryptedProviderUserId = providerIdCipher.encrypt(providerUserId);
        return KakaoLoginResult.additionalInfoRequired(
                signupSessionService.issue(identifier, encryptedProviderUserId),
                userInfo.nickname());
    }

    // row 존재는 현재 연결을 의미하지 않는다. 연결 상태와 User 제재를 각각 확인한 뒤에만 토큰을 발급한다.
    private KakaoLoginResult loginExistingMember(SocialAccount socialAccount) {
        User user = socialAccount.getUser();
        return KakaoLoginResult.loginCompleted(
                lockedTokenIssuanceService.issueForSocialAccount(
                        user.getId(), socialAccount.getId()));
    }

    /** 가입 토큰과 추가정보를 검증하고, 원자적 저장은 별도 트랜잭션 서비스에 위임한다. */
    public TokenIssueResult signup(String signupToken, KakaoSignupRequest request) {
        // 유효한 가입 세션 없이 전화번호·닉네임 중복 여부 같은 가입 검증 결과를 탐색하지 못하게 먼저 확인한다.
        // 실제 일회성 보장은 KakaoSignupTransactionService가 같은 세션을 다시 잠그고 검증한다.
        signupSessionService.validateUsable(signupToken);

        signupValidator.validateName(request.name());
        signupValidator.validateNickname(request.nickname());
        signupValidator.validateRequiredTermsAgreed(
                request.serviceTermsAgreed(), request.privacyPolicyAgreed());
        signupValidator.validateActivityRegionId(request.activityRegionId());
        signupValidator.validateInterestCategories(request.interestCategories());

        String phoneNumber = signupValidator.normalizePhoneNumber(request.phoneNumber());
        String nickname = request.nickname();
        String introduction = signupValidator.normalizeNullableText(request.introduction());

        signupValidator.validatePhoneNumberNotDuplicated(phoneNumber);
        signupValidator.validateNicknameNotDuplicated(nickname);

        Region activityRegion = signupValidator.findActivityRegion(request.activityRegionId());

        User user =
                User.createSocial(
                        request.name(),
                        request.birthDate(),
                        request.gender(),
                        phoneNumber,
                        nickname,
                        introduction,
                        request.serviceTermsAgreed(),
                        request.privacyPolicyAgreed(),
                        request.marketingAgreed(),
                        activityRegion,
                        request.interestCategories());

        try {
            return signupTransactionService.createAccount(user, signupToken, phoneNumber, nickname);
        } catch (KakaoSignupIdentityConflictException exception) {
            DataIntegrityViolationException integrityException = exception.integrityException();
            Optional<SocialAccount> conflictedAccount;
            try {
                SocialSignupIdentitySnapshot identity = exception.identity();
                conflictedAccount =
                        socialAccountIdentityService.findByProviderAndKey(
                                identity.provider(), identity.identifier());
            } catch (RuntimeException reloadFailure) {
                // 확정된 UNIQUE 충돌을 재조회 실패로 덮어쓰지 않는다. 원인은 suppressed로 보존해 운영 분석에 남긴다.
                integrityException.addSuppressed(reloadFailure);
                log.warn("소셜 계정 UNIQUE 충돌 후 재조회에 실패해 원본 무결성 오류를 유지합니다.", reloadFailure);
                throw integrityException;
            }
            if (conflictedAccount.isEmpty()) {
                log.error("소셜 계정 UNIQUE 충돌 후 최신 SocialAccount를 찾지 못해 원본 무결성 오류를 유지합니다.");
                throw integrityException;
            }
            SocialAccount socialAccount = conflictedAccount.get();
            if (!socialAccount.isLinked()) {
                throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
            }
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
    }

    // 부분 문자열이나 도메인 접미사 비교를 쓰면 공격자가 통제하는 URI로 인가 코드가 흘러갈 수 있다. 전체 문자열이 정확히 일치해야 한다.
    private void validateRedirectUri(String redirectUri) {
        if (!kakaoProperties.redirectUris().contains(redirectUri)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
