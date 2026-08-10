package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.kakao.client.KakaoApiClient;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.kakao.dto.KakaoLoginRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoSignupRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoUserResponse;
import com.gather.gather.domain.auth.kakao.dto.SignupStatus;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.AccountRejoinBlockService;
import com.gather.gather.domain.auth.service.LockedTokenIssuanceService;
import com.gather.gather.domain.auth.service.PhoneNumberPolicy;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.SocialAccountIdentityService;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private static final String PROVIDER_USER_KEY = "a".repeat(64);
    private static final String SIGNUP_TOKEN = "signup-token";
    private static final UUID PHONE_VERIFICATION_ID =
            UUID.fromString("5c5d5db1-4187-43d0-8580-672307994878");
    private static final long ACTIVITY_REGION_ID = 123L;
    private static final RejoinBlockIdentifier IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, PROVIDER_USER_KEY, 1);
    private static final EncryptedProviderUserId ENCRYPTED_PROVIDER_USER_ID =
            new EncryptedProviderUserId("encrypted-provider-user-id", 1);
    private static final SocialSignupIdentitySnapshot SIGNUP_IDENTITY =
            new SocialSignupIdentitySnapshot(
                    SocialProvider.KAKAO, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

    @Mock private KakaoApiClient kakaoApiClient;
    @Mock private SocialSignupSessionService signupSessionService;
    @Mock private RejoinBlockIdentifierHasher identifierHasher;
    @Mock private SocialAccountProviderIdCipher providerIdCipher;
    @Mock private SocialAccountIdentityService socialAccountIdentityService;
    @Mock private KakaoSignupTransactionService signupTransactionService;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private LockedTokenIssuanceService lockedTokenIssuanceService;
    @Mock private AccountRejoinBlockService accountRejoinBlockService;

    private KakaoAuthService kakaoAuthService;

    @BeforeEach
    void setUp() {
        kakaoAuthService =
                new KakaoAuthService(
                        kakaoApiClient,
                        properties(),
                        signupSessionService,
                        identifierHasher,
                        providerIdCipher,
                        socialAccountIdentityService,
                        signupTransactionService,
                        new SignupValidator(
                                userRepository, regionRepository, new PhoneNumberPolicy()),
                        lockedTokenIssuanceService,
                        accountRejoinBlockService,
                        Clock.fixed(Instant.parse("2026-07-31T05:25:56.123456Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("기존 카카오 회원은 가입 토큰 없이 곧바로 로그인된다")
    void login_whenSocialAccountExists_returnsLoginCompleted() {
        User user = socialUser();
        stubKakaoAuthentication("동현");
        when(socialAccountIdentityService.findKakaoAccount(PROVIDER_USER_ID, IDENTIFIER))
                .thenReturn(Optional.of(linkedSocialAccount(user)));
        when(lockedTokenIssuanceService.issueForSocialAccount(user.getId(), null))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        KakaoLoginResult result = kakaoAuthService.login(loginRequest());

        assertThat(result.signupStatus()).isEqualTo(SignupStatus.LOGIN_COMPLETED);
        assertThat(result.tokens().accessToken()).isEqualTo("access-token");
        assertThat(result.signupToken()).isNull();
        verify(signupSessionService, never()).issue(any(), any());
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

    @Test
    @DisplayName("탈퇴 처리 중인 카카오 회원은 로그인을 거부하고 토큰을 발급하지 않는다")
    void login_whenWithdrawalPendingMember_throwsPendingUserAndIssuesNoToken() {
        assertKakaoLoginBlockedByStatus(
                UserStatus.WITHDRAWAL_PENDING, ErrorCode.WITHDRAWAL_PENDING_USER);
    }

    @Test
    @DisplayName("LINKED가 아닌 SocialAccount는 User가 ACTIVE여도 로그인시키지 않는다")
    void login_whenSocialAccountNotLinked_throwsConflictAndIssuesNoToken() {
        User user = socialUser();
        SocialAccount account = linkedSocialAccount(user);
        account.markUnlinkPending(LocalDateTime.of(2026, 7, 29, 13, 0));
        stubKakaoAuthentication("동현");
        when(socialAccountIdentityService.findKakaoAccount(PROVIDER_USER_ID, IDENTIFIER))
                .thenReturn(Optional.of(account));
        when(lockedTokenIssuanceService.issueForSocialAccount(user.getId(), account.getId()))
                .thenThrow(new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED));

        assertErrorCode(
                () -> kakaoAuthService.login(loginRequest()), ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);

        verify(lockedTokenIssuanceService).issueForSocialAccount(user.getId(), account.getId());
    }

    private void assertKakaoLoginBlockedByStatus(UserStatus status, ErrorCode expected) {
        User user = mock(User.class);
        stubKakaoAuthentication("동현");
        when(socialAccountIdentityService.findKakaoAccount(PROVIDER_USER_ID, IDENTIFIER))
                .thenReturn(Optional.of(linkedSocialAccount(user)));
        when(lockedTokenIssuanceService.issueForSocialAccount(user.getId(), null))
                .thenThrow(new BusinessException(expected));

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
    @DisplayName("재가입 제한 중인 카카오 식별자는 가입 세션을 발급하지 않는다")
    void login_whenKakaoRejoinBlocked_rejectsBeforeSignupSessionIssue() {
        stubKakaoAuthentication("동현");
        when(socialAccountIdentityService.findKakaoAccount(PROVIDER_USER_ID, IDENTIFIER))
                .thenReturn(Optional.empty());
        when(accountRejoinBlockService.isKakaoBlocked(
                        eq(PROVIDER_USER_ID), any(LocalDateTime.class)))
                .thenReturn(true);

        assertErrorCode(
                () -> kakaoAuthService.login(loginRequest()), ErrorCode.ACCOUNT_REJOIN_BLOCKED);

        verify(signupSessionService, never()).issue(any(), any());
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
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID)).thenReturn(Optional.of(activityRegion));
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        TokenIssueResult result = kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest());

        verify(signupSessionService).validateUsable(SIGNUP_TOKEN);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(signupTransactionService)
                .createAccount(
                        userCaptor.capture(),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동"));
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getPassword()).isNull();
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.getActivityRegion()).isSameAs(activityRegion);
        assertThat(savedUser.getInterestCategories()).containsExactly(PostingCategory.WELFARE);

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("이미 가입된 카카오 회원번호로 가입을 시도하면 ALREADY_REGISTERED로 실패한다")
    void signup_whenAlreadyRegistered_throwsAlreadyRegistered() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("강남구", 2, "11680", null)));
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenThrow(new BusinessException(ErrorCode.ALREADY_REGISTERED));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("사전 검사를 통과한 동시 요청이 SocialAccount 유니크 제약에 걸리면 ALREADY_REGISTERED로 변환한다")
    void signup_whenSocialAccountUniqueViolation_throwsAlreadyRegistered() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("강남구", 2, "11680", null)));
        DataIntegrityViolationException integrityException =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_provider_key'");
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenThrow(
                        new KakaoSignupIdentityConflictException(
                                SIGNUP_IDENTITY, integrityException));
        when(socialAccountIdentityService.findByProviderAndKey(SocialProvider.KAKAO, IDENTIFIER))
                .thenReturn(Optional.of(linkedSocialAccount(socialUser())));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("UNIQUE 충돌 후 UNLINK_PENDING 계정이 조회되면 재가입을 거부한다")
    void signup_whenConflictReloadsUnlinkPending_throwsNotLinked() {
        stubValidSignupToken();
        stubValidSignupInput();
        DataIntegrityViolationException integrityException =
                new DataIntegrityViolationException("provider identity conflict");
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenThrow(
                        new KakaoSignupIdentityConflictException(
                                SIGNUP_IDENTITY, integrityException));
        SocialAccount account = linkedSocialAccount(socialUser());
        account.markUnlinkPending(LocalDateTime.of(2026, 7, 29, 13, 0));
        when(socialAccountIdentityService.findByProviderAndKey(SocialProvider.KAKAO, IDENTIFIER))
                .thenReturn(Optional.of(account));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
    }

    @Test
    @DisplayName("UNIQUE 충돌 후 UNLINKED 계정이 조회되면 재가입을 거부한다")
    void signup_whenConflictReloadsUnlinked_throwsNotLinked() {
        stubValidSignupToken();
        stubValidSignupInput();
        DataIntegrityViolationException integrityException =
                new DataIntegrityViolationException("provider identity conflict");
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenThrow(
                        new KakaoSignupIdentityConflictException(
                                SIGNUP_IDENTITY, integrityException));
        SocialAccount account = linkedSocialAccount(socialUser());
        account.markUnlinkPending(LocalDateTime.of(2026, 7, 29, 13, 0));
        account.markUnlinked(LocalDateTime.of(2026, 7, 29, 14, 0));
        when(socialAccountIdentityService.findByProviderAndKey(SocialProvider.KAKAO, IDENTIFIER))
                .thenReturn(Optional.of(account));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
    }

    @Test
    @DisplayName("provider key UNIQUE 충돌 후 재조회 결과가 없으면 원래 무결성 예외를 유지한다")
    void signup_whenProviderKeyConflictCannotBeReloaded_rethrowsIntegrityException() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("강남구", 2, "11680", null)));
        DataIntegrityViolationException integrityException =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_provider_key'");
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenThrow(
                        new KakaoSignupIdentityConflictException(
                                SIGNUP_IDENTITY, integrityException));
        when(socialAccountIdentityService.findByProviderAndKey(SocialProvider.KAKAO, IDENTIFIER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()))
                .isSameAs(integrityException);
    }

    @Test
    @DisplayName("provider key UNIQUE 충돌 후 재조회 실패는 원래 무결성 예외를 유지한다")
    void signup_whenProviderKeyConflictReloadFails_preservesIntegrityException() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("강남구", 2, "11680", null)));
        DataIntegrityViolationException integrityException =
                new DataIntegrityViolationException(
                        "Duplicate entry for key 'social_account.uk_social_account_provider_key'");
        IllegalStateException reloadFailure = new IllegalStateException("key version mismatch");
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenThrow(
                        new KakaoSignupIdentityConflictException(
                                SIGNUP_IDENTITY, integrityException));
        when(socialAccountIdentityService.findByProviderAndKey(SocialProvider.KAKAO, IDENTIFIER))
                .thenThrow(reloadFailure);

        assertThatThrownBy(() -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()))
                .isSameAs(integrityException)
                .satisfies(
                        exception -> assertThat(exception.getSuppressed()).contains(reloadFailure));
    }

    @Test
    @DisplayName("기존 일반 회원과 전화번호가 겹치면 DUPLICATE_PHONE_NUMBER로 실패한다")
    void signup_whenPhoneNumberDuplicated_throwsDuplicatePhoneNumber() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(true);

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.DUPLICATE_PHONE_NUMBER);

        verifyNoInteractions(signupTransactionService);
    }

    @Test
    @DisplayName("무효한 가입 token은 사용자 입력 조회와 저장 흐름 전에 차단한다")
    void signup_whenSignupTokenInvalid_stopsBeforeInputValidationAndPersistence() {
        BusinessException invalid = new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        doThrow(invalid).when(signupSessionService).validateUsable(SIGNUP_TOKEN);

        assertThatThrownBy(() -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()))
                .isSameAs(invalid);

        verifyNoInteractions(
                userRepository, regionRepository, signupTransactionService, tokenIssuer);
    }

    @Test
    @DisplayName("만료된 가입 token은 사용자 입력 조회와 저장 흐름 전에 차단한다")
    void signup_whenSignupTokenExpired_stopsBeforeInputValidationAndPersistence() {
        BusinessException expired = new BusinessException(ErrorCode.SIGNUP_TOKEN_EXPIRED);
        doThrow(expired).when(signupSessionService).validateUsable(SIGNUP_TOKEN);

        assertThatThrownBy(() -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()))
                .isSameAs(expired);

        verifyNoInteractions(
                userRepository, regionRepository, signupTransactionService, tokenIssuer);
    }

    @Test
    @DisplayName("닉네임이 중복되면 DUPLICATE_NICKNAME으로 실패한다")
    void signup_whenNicknameDuplicated_throwsDuplicateNickname() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(true);

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않으면 실패한다")
    void signup_whenRequiredTermsNotAgreed_throwsRequiredTermsNotAgreed() {
        stubValidSignupToken();

        KakaoSignupRequest request =
                new KakaoSignupRequest(
                        "홍길동",
                        LocalDate.of(2002, 3, 15),
                        Gender.MALE,
                        "01012345678",
                        PHONE_VERIFICATION_ID,
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

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest(List.of())),
                ErrorCode.INVALID_INTEREST_CATEGORY_COUNT);
    }

    @Test
    @DisplayName("존재하지 않는 활동 지역이면 실패한다")
    void signup_whenActivityRegionNotFound_throwsRegionNotFound() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID)).thenReturn(Optional.empty());

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    @DisplayName("시도(level 1) 활동 지역을 선택해도 가입에 성공한다")
    void signup_whenActivityRegionIsSido_succeeds() {
        Region activityRegion = Region.create("서울특별시", 1, "11", null);
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID)).thenReturn(Optional.of(activityRegion));
        when(signupTransactionService.createAccount(
                        any(User.class),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동")))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));

        kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(signupTransactionService)
                .createAccount(
                        userCaptor.capture(),
                        eq(SIGNUP_TOKEN),
                        eq(PHONE_VERIFICATION_ID),
                        eq("01012345678"),
                        eq("길동"));
        assertThat(userCaptor.getValue().getActivityRegion()).isSameAs(activityRegion);
    }

    @Test
    @DisplayName("읍/면/동(level 4) 활동 지역이면 실패한다")
    void signup_whenActivityRegionIsEupmyeondong_throwsRegionNotFound() {
        stubValidSignupToken();
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("역삼동", 4, "1168010100", null)));

        assertErrorCode(
                () -> kakaoAuthService.signup(SIGNUP_TOKEN, signupRequest()),
                ErrorCode.REGION_NOT_FOUND);
    }

    private void stubKakaoAuthentication(String nickname) {
        when(kakaoApiClient.requestAccessToken("auth-code", REDIRECT_URI))
                .thenReturn("kakao-access-token");
        when(kakaoApiClient.getUserInfo("kakao-access-token")).thenReturn(kakaoUser(nickname));
        when(identifierHasher.hashKakao(PROVIDER_USER_ID)).thenReturn(IDENTIFIER);
    }

    private void stubNewSocialAccount() {
        when(socialAccountIdentityService.findKakaoAccount(PROVIDER_USER_ID, IDENTIFIER))
                .thenReturn(Optional.empty());
        when(providerIdCipher.encrypt(PROVIDER_USER_ID)).thenReturn(ENCRYPTED_PROVIDER_USER_ID);
        when(signupSessionService.issue(IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID))
                .thenReturn(SIGNUP_TOKEN);
    }

    private void stubValidSignupToken() {
        doNothing().when(signupSessionService).validateUsable(SIGNUP_TOKEN);
    }

    private void stubValidSignupInput() {
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(false);
        when(userRepository.existsByNickname("길동")).thenReturn(false);
        when(regionRepository.findById(ACTIVITY_REGION_ID))
                .thenReturn(Optional.of(Region.create("강남구", 2, "11680", null)));
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
                PHONE_VERIFICATION_ID,
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

    private SocialAccount linkedSocialAccount(User user) {
        return SocialAccount.createLinked(
                user,
                SocialProvider.KAKAO,
                PROVIDER_USER_ID,
                PROVIDER_USER_KEY,
                1,
                ENCRYPTED_PROVIDER_USER_ID,
                LocalDateTime.of(2026, 7, 29, 12, 0));
    }

    private KakaoProperties properties() {
        return new KakaoProperties(
                "test-rest-api-key",
                "test-client-secret",
                List.of(REDIRECT_URI),
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
