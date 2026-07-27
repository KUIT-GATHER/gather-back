package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.client.KakaoApiClient;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.dto.KakaoLoginRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoSignupRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoUserResponse;
import com.gather.gather.domain.auth.kakao.dto.SignupStatus;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenPayload;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenProvider;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.LoginPolicy;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class KakaoAuthServiceTest {

    private static final String REDIRECT_URI = "https://gathernow.kr/login/kakao/callback";
    private static final String PROVIDER_USER_ID = "123456789";
    private static final String SIGNUP_TOKEN = "signup-token";
    private static final long ACTIVITY_REGION_ID = 123L;
    private static final String SIGNUP_TOKEN_SECRET =
            "z9tOf6reUdkTRI0KFFiydLKdxpayBBxVWSAm7EJTgKXolFCFvnQ4qViBrdh6y7yP";

    @Mock private KakaoApiClient kakaoApiClient;
    @Mock private SocialSignupTokenProvider socialSignupTokenProvider;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private TokenIssuer tokenIssuer;

    private KakaoAuthService kakaoAuthService;

    @BeforeEach
    void setUp() {
        kakaoAuthService =
                new KakaoAuthService(
                        kakaoApiClient,
                        properties(),
                        socialSignupTokenProvider,
                        socialAccountRepository,
                        userRepository,
                        new SignupValidator(userRepository, regionRepository),
                        tokenIssuer,
                        new LoginPolicy());
    }

    @Test
    @DisplayName("기존 카카오 회원은 가입 토큰 없이 곧바로 로그인된다")
    void login_whenSocialAccountExists_returnsLoginCompleted() {
        User user = socialUser();
        stubKakaoAuthentication("동현");
        when(socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(
                        Optional.of(
                                SocialAccount.create(
                                        user, SocialProvider.KAKAO, PROVIDER_USER_ID)));
        when(tokenIssuer.issue(user))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        KakaoLoginResult result = kakaoAuthService.login(loginRequest());

        assertThat(result.signupStatus()).isEqualTo(SignupStatus.LOGIN_COMPLETED);
        assertThat(result.tokens().accessToken()).isEqualTo("access-token");
        assertThat(result.signupToken()).isNull();
        verify(socialSignupTokenProvider, never()).createSignupToken(any(), any());
    }

    @Test
    @DisplayName("정지된 카카오 회원은 로그인을 거부하고 토큰을 발급하지 않는다")
    void login_whenSuspendedMember_throwsSuspendedUserAndIssuesNoToken() {
        assertKakaoLoginBlockedByStatus(UserStatus.SUSPENDED, ErrorCode.SUSPENDED_USER);
    }

    @Test
    @DisplayName("탈퇴한 카카오 회원은 로그인을 거부하고 토큰을 발급하지 않는다")
    void login_whenWithdrawnMember_throwsWithdrawnUserAndIssuesNoToken() {
        assertKakaoLoginBlockedByStatus(UserStatus.WITHDRAWN, ErrorCode.WITHDRAWN_USER);
    }

    private void assertKakaoLoginBlockedByStatus(UserStatus status, ErrorCode expected) {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(status);
        stubKakaoAuthentication("동현");
        when(socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(
                        Optional.of(
                                SocialAccount.create(
                                        user, SocialProvider.KAKAO, PROVIDER_USER_ID)));

        assertErrorCode(() -> kakaoAuthService.login(loginRequest()), expected);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("신규 카카오 회원은 로그인되지 않고 가입 토큰과 닉네임 초깃값을 받는다")
    void login_whenSocialAccountAbsent_returnsAdditionalInfoRequired() {
        stubKakaoAuthentication("동현");
        stubNewSocialAccount();

        KakaoLoginResult result = kakaoAuthService.login(loginRequest());

        assertThat(result.signupStatus()).isEqualTo(SignupStatus.ADDITIONAL_INFO_REQUIRED);
        assertThat(result.signupToken()).isEqualTo(SIGNUP_TOKEN);
        assertThat(result.nickname()).isEqualTo("동현");
        assertThat(result.tokens()).isNull();
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("카카오 닉네임이 없어도 가입 토큰은 발급된다")
    void login_whenNicknameMissing_returnsNullNickname() {
        stubKakaoAuthentication(null);
        stubNewSocialAccount();

        KakaoLoginResult result = kakaoAuthService.login(loginRequest());

        assertThat(result.signupStatus()).isEqualTo(SignupStatus.ADDITIONAL_INFO_REQUIRED);
        assertThat(result.nickname()).isNull();
    }

    @Test
    @DisplayName("허용 목록에 없는 redirectUri는 카카오를 호출하기 전에 거부한다")
    void login_whenRedirectUriNotAllowed_rejectsBeforeCallingKakao() {
        assertErrorCode(
                () ->
                        kakaoAuthService.login(
                                new KakaoLoginRequest(
                                        "auth-code",
                                        "https://evil.example.com/login/kakao/callback")),
                ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(kakaoApiClient);
    }

    @Test
    @DisplayName("허용 목록 값의 접두사만 같은 redirectUri도 거부한다")
    void login_whenRedirectUriIsPrefixOnly_rejects() {
        assertErrorCode(
                () ->
                        kakaoAuthService.login(
                                new KakaoLoginRequest("auth-code", REDIRECT_URI + "/")),
                ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(kakaoApiClient);
    }

    @Test
    @DisplayName("추가정보 가입은 이메일·비밀번호 없이 User와 SocialAccount를 만들고 토큰을 발급한다")
    void signup_createsSocialUserAndAccount() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);
        when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID)).thenReturn(Optional.of(activityRegion));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenIssuer.issue(any(User.class)))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        TokenIssueResult result = kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getPassword()).isNull();
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.getActivityRegion()).isSameAs(activityRegion);
        assertThat(savedUser.getInterestCategories()).containsExactly(PostingCategory.WELFARE);

        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo(PROVIDER_USER_ID);
        assertThat(accountCaptor.getValue().getUser()).isSameAs(savedUser);

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("이미 가입된 카카오 회원번호로 가입을 시도하면 ALREADY_REGISTERED로 실패한다")
    void signup_whenAlreadyRegistered_throwsAlreadyRegistered() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(true);

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.ALREADY_REGISTERED);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("사전 검사를 통과한 동시 요청이 SocialAccount 유니크 제약에 걸리면 ALREADY_REGISTERED로 변환한다")
    void signup_whenSocialAccountUniqueViolation_throwsAlreadyRegistered() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);
        when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("강남구", 2, "11680", null)));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Duplicate entry 'KAKAO-123456789' for key"
                                        + " 'social_account.uk_social_account_provider_user'"));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("기존 일반 회원과 전화번호가 겹치면 DUPLICATE_PHONE_NUMBER로 실패한다")
    void signup_whenPhoneNumberDuplicated_throwsDuplicatePhoneNumber() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);
        when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(socialUser()));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.DUPLICATE_PHONE_NUMBER);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("탈퇴자가 쥔 전화번호로 카카오 가입하면 재가입 유예 오류가 난다 (일반 가입과 같은 검증을 공유한다)")
    void signup_whenPhoneNumberHeldByWithdrawnUser_throwsCooldown() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);
        User withdrawn = socialUser();
        withdrawn.withdraw(WithdrawalReason.SELF, LocalDateTime.now().minusDays(1));
        when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(withdrawn));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.WITHDRAWN_PHONE_NUMBER_COOLDOWN);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("닉네임이 중복되면 DUPLICATE_NICKNAME으로 실패한다")
    void signup_whenNicknameDuplicated_throwsDuplicateNickname() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);
        when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("길동")).thenReturn(true);

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않으면 실패한다")
    void signup_whenRequiredTermsNotAgreed_throwsRequiredTermsNotAgreed() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);

        KakaoSignupRequest request =
                new KakaoSignupRequest(
                        "홍길동",
                        LocalDate.of(2002, 3, 15),
                        Gender.MALE,
                        "01012345678",
                        "길동",
                        null,
                        ACTIVITY_REGION_ID,
                        List.of(PostingCategory.WELFARE),
                        true,
                        false,
                        false);

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, request),
                ErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    @Test
    @DisplayName("관심 카테고리가 비어 있으면 실패한다")
    void signup_whenInterestCategoriesEmpty_throwsInvalidInterestCategoryCount() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest(List.of())),
                ErrorCode.INVALID_INTEREST_CATEGORY_COUNT);
    }

    @Test
    @DisplayName("존재하지 않는 활동 지역이면 실패한다")
    void signup_whenActivityRegionNotFound_throwsRegionNotFound() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);
        when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID)).thenReturn(Optional.empty());

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    @DisplayName("시군구(level 2)가 아닌 활동 지역이면 실패한다")
    void signup_whenActivityRegionIsNotSigungu_throwsRegionNotFound() {
        stubValidSignupToken();
        when(socialAccountRepository.existsByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(false);
        when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("서울특별시", 1, "11", null)));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.REGION_NOT_FOUND);
    }

    private void stubKakaoAuthentication(String nickname) {
        when(kakaoApiClient.requestAccessToken("auth-code", REDIRECT_URI))
                .thenReturn("kakao-access-token");
        when(kakaoApiClient.getUserInfo("kakao-access-token")).thenReturn(kakaoUser(nickname));
    }

    private void stubNewSocialAccount() {
        when(socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.empty());
        when(socialSignupTokenProvider.createSignupToken(SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(SIGNUP_TOKEN);
    }

    private void stubValidSignupToken() {
        when(socialSignupTokenProvider.parseSignupToken(SIGNUP_TOKEN))
                .thenReturn(new SocialSignupTokenPayload(SocialProvider.KAKAO, PROVIDER_USER_ID));
    }

    private KakaoUserResponse kakaoUser(String nickname) {
        return new KakaoUserResponse(
                Long.valueOf(PROVIDER_USER_ID),
                new KakaoUserResponse.KakaoAccount(
                        new KakaoUserResponse.KakaoAccount.Profile(nickname)));
    }

    private KakaoLoginRequest loginRequest() {
        return new KakaoLoginRequest("auth-code", REDIRECT_URI);
    }

    private KakaoSignupRequest signupRequest() {
        return signupRequest(List.of(PostingCategory.WELFARE));
    }

    private KakaoSignupRequest signupRequest(List<PostingCategory> interestCategories) {
        return new KakaoSignupRequest(
                "홍길동",
                LocalDate.of(2002, 3, 15),
                Gender.MALE,
                "01012345678",
                "길동",
                null,
                ACTIVITY_REGION_ID,
                interestCategories,
                true,
                true,
                false);
    }

    private User socialUser() {
        return User.createSocial(
                "홍길동",
                LocalDate.of(2002, 3, 15),
                Gender.MALE,
                "01012345678",
                "길동",
                null,
                true,
                true,
                false,
                Region.create("강남구", 2, "11680", null),
                List.of(PostingCategory.WELFARE));
    }

    private KakaoProperties properties() {
        return new KakaoProperties(
                "test-rest-api-key",
                "test-client-secret",
                "test-kakao-admin-key-0123456789a",
                "1234567",
                List.of(REDIRECT_URI),
                SIGNUP_TOKEN_SECRET,
                900,
                "https://kauth.kakao.com",
                "https://kapi.kakao.com");
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
