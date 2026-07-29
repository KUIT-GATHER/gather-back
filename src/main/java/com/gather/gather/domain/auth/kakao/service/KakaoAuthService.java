package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.client.KakaoApiClient;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.dto.KakaoLoginRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoSignupRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoUserResponse;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenPayload;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenProvider;
import com.gather.gather.domain.auth.service.LoginPolicy;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.SocialAccountIdentityService;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.auth.service.SocialAccountProviderKeyConflictException;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final KakaoApiClient kakaoApiClient;
    private final KakaoProperties kakaoProperties;
    private final SocialSignupTokenProvider socialSignupTokenProvider;
    private final RejoinBlockIdentifierHasher identifierHasher;
    private final SocialAccountProviderIdCipher providerIdCipher;
    private final SocialAccountIdentityService socialAccountIdentityService;
    private final KakaoSignupTransactionService signupTransactionService;
    private final SignupValidator signupValidator;
    private final TokenIssuer tokenIssuer;
    private final LoginPolicy loginPolicy;

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
        RejoinBlockIdentifier identifier = identifierHasher.hashKakao(providerUserId);

        return socialAccountIdentityService
                .findKakaoAccount(providerUserId, identifier)
                .map(this::loginExistingMember)
                .orElseGet(
                        () -> {
                            EncryptedProviderUserId encryptedProviderUserId =
                                    providerIdCipher.encrypt(providerUserId);
                            return KakaoLoginResult.additionalInfoRequired(
                                    socialSignupTokenProvider.createSignupToken(
                                            SocialProvider.KAKAO,
                                            identifier,
                                            encryptedProviderUserId),
                                    userInfo.nickname());
                        });
    }

    // 정지·탈퇴 계정이 카카오 로그인으로 제재를 우회하지 못하도록, 일반 로그인·재발급과 동일하게 상태를 검증한 뒤 토큰을 발급한다.
    private KakaoLoginResult loginExistingMember(SocialAccount socialAccount) {
        if (!socialAccount.isLinked()) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
        }
        User user = socialAccount.getUser();
        loginPolicy.validateLoginAllowed(user);
        return KakaoLoginResult.loginCompleted(tokenIssuer.issue(user));
    }

    /**
     * User·SocialAccount·활동지역·관심 카테고리·약관·Refresh Token을 한 트랜잭션으로 저장한다. 중간에 실패하면 회원이 일부만 생성되지 않는다.
     */
    public TokenIssueResult signup(String signupToken, KakaoSignupRequest request) {
        SocialSignupTokenPayload payload = socialSignupTokenProvider.parseSignupToken(signupToken);
        validateNotRegistered(payload);

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
            return signupTransactionService.createAccount(user, payload, phoneNumber, nickname);
        } catch (SocialAccountProviderKeyConflictException exception) {
            if (socialAccountIdentityService
                    .findByProviderAndKey(payload.provider(), payload.identifier())
                    .isEmpty()) {
                throw exception.getIntegrityException();
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

    // 만료 전 가입 토큰은 여러 번 제출될 수 있다. 유니크 제약에 도달하기 전에 명확한 코드로 실패시킨다.
    private void validateNotRegistered(SocialSignupTokenPayload payload) {
        if (socialAccountIdentityService
                .findByProviderAndKey(payload.provider(), payload.identifier())
                .isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
    }
}
