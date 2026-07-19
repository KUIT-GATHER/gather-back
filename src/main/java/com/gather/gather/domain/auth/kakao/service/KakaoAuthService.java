package com.gather.gather.domain.auth.kakao.service;

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
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.LoginPolicy;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final KakaoApiClient kakaoApiClient;
    private final KakaoProperties kakaoProperties;
    private final SocialSignupTokenProvider socialSignupTokenProvider;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final SignupValidator signupValidator;
    private final TokenIssuer tokenIssuer;
    private final LoginPolicy loginPolicy;

    /**
     * 카카오 인증 결과로 기존 회원 로그인과 신규 회원 가입 토큰 발급을 분기한다.
     *
     * <p>카카오 API 호출 두 번이 포함돼 있어 트랜잭션을 열지 않는다. 유일한 쓰기인 Refresh Token 저장은 리포지토리 자체 트랜잭션으로 처리되므로, 외부
     * 응답을 기다리는 동안 DB 커넥션을 붙잡지 않는다.
     */
    public KakaoLoginResult login(KakaoLoginRequest request) {
        validateRedirectUri(request.redirectUri());

        String kakaoAccessToken =
                kakaoApiClient.requestAccessToken(
                        request.authorizationCode(), request.redirectUri());
        KakaoUserResponse userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
        String providerUserId = String.valueOf(userInfo.id());

        return socialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.KAKAO, providerUserId)
                .map(socialAccount -> loginExistingMember(socialAccount.getUser()))
                .orElseGet(
                        () ->
                                KakaoLoginResult.additionalInfoRequired(
                                        socialSignupTokenProvider.createSignupToken(
                                                SocialProvider.KAKAO, providerUserId),
                                        userInfo.nickname()));
    }

    // 정지·탈퇴 계정이 카카오 로그인으로 제재를 우회하지 못하도록, 일반 로그인·재발급과 동일하게 상태를 검증한 뒤 토큰을 발급한다.
    private KakaoLoginResult loginExistingMember(User user) {
        loginPolicy.validateLoginAllowed(user);
        return KakaoLoginResult.loginCompleted(tokenIssuer.issue(user));
    }

    /**
     * User·SocialAccount·활동지역·관심 카테고리·약관·Refresh Token을 한 트랜잭션으로 저장한다. 중간에 실패하면 회원이 일부만 생성되지 않는다.
     */
    @Transactional
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

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(exception, null, phoneNumber, nickname);
        }

        try {
            socialAccountRepository.saveAndFlush(
                    SocialAccount.create(savedUser, payload.provider(), payload.providerUserId()));
        } catch (DataIntegrityViolationException exception) {
            // 사전 재조회를 통과한 동시 요청이 uk_social_account_provider_user에서 충돌한 경우 = 같은 가입 토큰의 중복 사용.
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        return tokenIssuer.issue(savedUser);
    }

    // 부분 문자열이나 도메인 접미사 비교를 쓰면 공격자가 통제하는 URI로 인가 코드가 흘러갈 수 있다. 전체 문자열이 정확히 일치해야 한다.
    private void validateRedirectUri(String redirectUri) {
        if (!kakaoProperties.redirectUris().contains(redirectUri)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    // 만료 전 가입 토큰은 여러 번 제출될 수 있다. 유니크 제약에 도달하기 전에 명확한 코드로 실패시킨다.
    private void validateNotRegistered(SocialSignupTokenPayload payload) {
        if (socialAccountRepository.existsByProviderAndProviderUserId(
                payload.provider(), payload.providerUserId())) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
    }
}
