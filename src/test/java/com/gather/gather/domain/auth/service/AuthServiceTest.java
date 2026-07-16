package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailSender emailSender;
    @Mock private TokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService =
                new AuthService(
                        userRepository,
                        emailVerificationRepository,
                        refreshTokenRepository,
                        regionRepository,
                        passwordEncoder,
                        emailSender,
                        tokenProvider);
    }

    @Test
    @DisplayName("회원가입은 level=2 시군구 활동 지역 1개를 User에 저장한다")
    void signup_withLevel2ActivityRegion_savesUserActivityRegion() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        prepareVerifiedEmail();
        when(regionRepository.findById(123L)).thenReturn(Optional.of(activityRegion));
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(signupRequest(123L));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getActivityRegion()).isSameAs(activityRegion);
        assertThat(captor.getValue().getInterestCategories())
                .containsExactly(PostingCategory.WELFARE);
    }

    @Test
    @DisplayName("회원가입에서 관심 카테고리가 null이면 실패한다")
    void signup_withNullInterestCategories_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(null);
    }

    @Test
    @DisplayName("회원가입에서 null 관심 카테고리가 포함되면 실패한다")
    void signup_withNullInterestCategory_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(Collections.singletonList(null));
    }

    @Test
    @DisplayName("회원가입에서 관심 카테고리가 비어 있으면 실패한다")
    void signup_withEmptyInterestCategories_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(List.of());
    }

    @Test
    @DisplayName("회원가입에서 관심 카테고리가 중복되면 실패한다")
    void signup_withDuplicateInterestCategories_throwsInvalidInterestCategoryCount() {
        assertInvalidInterestCategories(List.of(PostingCategory.WELFARE, PostingCategory.WELFARE));
    }

    @Test
    @DisplayName("회원가입에서 level=1 시도를 활동 지역으로 선택하면 실패한다")
    void signup_withLevel1ActivityRegion_throwsRegionNotFound() {
        prepareVerifiedEmail();
        when(regionRepository.findById(1L))
                .thenReturn(Optional.of(Region.create("서울", 1, "11", null)));

        assertThatThrownBy(() -> authService.signup(signupRequest(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REGION_NOT_FOUND));
    }

    @Test
    @DisplayName("회원가입에서 존재하지 않는 활동 지역 ID를 선택하면 실패한다")
    void signup_withUnknownActivityRegion_throwsRegionNotFound() {
        prepareVerifiedEmail();
        when(regionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signup(signupRequest(999L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REGION_NOT_FOUND));
    }

    @Test
    @DisplayName("회원가입에서 활동 지역 ID가 null이면 실패한다")
    void signup_withNullActivityRegion_throwsInvalidActivityRegion() {
        assertThatThrownBy(() -> authService.signup(signupRequest(null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_ACTIVITY_REGION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나", "가나다라마바사아자차", "Ab", "abcdefghijklmnopqrst"})
    @DisplayName("회원가입은 정책에 맞는 이름을 허용한다")
    void signup_withValidName_savesName(String name) {
        prepareSuccessfulSignup();

        authService.signup(signupRequest(123L, name, "길동"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(name);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "가",
                "가나다라마바사아자차카",
                "A",
                "abcdefghijklmnopqrstu",
                "홍John",
                "John1",
                "John!",
                " John",
                "John Smith",
                "ㅎㄱ"
            })
    @DisplayName("회원가입은 정책에 맞지 않는 이름을 거부한다")
    void signup_withInvalidName_throwsValidationError(String name) {
        assertValidationError(signupRequest(123L, name, "길동"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나", "가나다라마바사아자차", "Ab", "abcdefghijklmnopqrst"})
    @DisplayName("회원가입은 정책에 맞는 닉네임을 허용한다")
    void signup_withValidNickname_savesNickname(String nickname) {
        prepareSuccessfulSignup();

        authService.signup(signupRequest(123L, "홍길동", nickname));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo(nickname);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "가",
                "가나다라마바사아자차카",
                "A",
                "abcdefghijklmnopqrstu",
                "홍John",
                "John1",
                "John!",
                " John",
                "John Smith",
                "ㅎㄱ"
            })
    @DisplayName("회원가입은 정책에 맞지 않는 닉네임을 거부한다")
    void signup_withInvalidNickname_throwsValidationError(String nickname) {
        assertValidationError(signupRequest(123L, "홍길동", nickname));
    }

    @Test
    @DisplayName("reissue는 기존 Refresh Token을 revoke하고 새 토큰을 저장한다")
    void reissue_revokesOldRefreshTokenAndStoresNewRefreshToken() {
        User user = activeUser();
        RefreshToken oldRefreshToken =
                RefreshToken.create("old-refresh-hash", user, LocalDateTime.now().plusDays(1));
        when(tokenProvider.hashToken("old-refresh-token")).thenReturn("old-refresh-hash");
        when(refreshTokenRepository.findByTokenHash("old-refresh-hash"))
                .thenReturn(Optional.of(oldRefreshToken));
        when(tokenProvider.createAccessToken(user)).thenReturn("new-access-token");
        when(tokenProvider.generateToken()).thenReturn("new-refresh-token");
        when(tokenProvider.hashToken("new-refresh-token")).thenReturn("new-refresh-hash");
        when(tokenProvider.refreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(14));

        TokenIssueResult result = authService.reissue("old-refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(oldRefreshToken.isRevoked()).isTrue();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("new-refresh-hash");
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("이미 revoke된 Refresh Token으로 reissue하면 REVOKED_TOKEN이고 새 토큰을 저장하지 않는다")
    void reissue_withRevokedRefreshToken_throwsRevokedToken() {
        RefreshToken revokedRefreshToken =
                RefreshToken.create(
                        "old-refresh-hash", activeUser(), LocalDateTime.now().plusDays(1));
        revokedRefreshToken.revoke(LocalDateTime.now());
        when(tokenProvider.hashToken("old-refresh-token")).thenReturn("old-refresh-hash");
        when(refreshTokenRepository.findByTokenHash("old-refresh-hash"))
                .thenReturn(Optional.of(revokedRefreshToken));

        assertThatThrownBy(() -> authService.reissue("old-refresh-token"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REVOKED_TOKEN));

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("logout은 유효한 Refresh Token을 revoke한다")
    void logout_revokesRefreshToken() {
        RefreshToken refreshToken =
                RefreshToken.create("refresh-hash", activeUser(), LocalDateTime.now().plusDays(1));
        when(tokenProvider.hashToken("refresh-token")).thenReturn("refresh-hash");
        when(refreshTokenRepository.findByTokenHash("refresh-hash"))
                .thenReturn(Optional.of(refreshToken));

        authService.logout("refresh-token");

        assertThat(refreshToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 Refresh Token으로 logout하면 INVALID_TOKEN이다")
    void logout_withUnknownRefreshToken_throwsInvalidToken() {
        when(tokenProvider.hashToken("unknown-refresh-token")).thenReturn("unknown-refresh-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-refresh-hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("unknown-refresh-token"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    private static User activeUser() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        return User.create(
                "홍길동",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                "01012345678",
                "test@example.com",
                "encoded-password",
                "길동",
                null,
                true,
                true,
                false,
                activityRegion,
                List.of());
    }

    private void prepareVerifiedEmail() {
        EmailVerification emailVerification =
                EmailVerification.create(
                        "test@example.com", "123456", LocalDateTime.now().plusMinutes(10));
        emailVerification.verify(LocalDateTime.now());
        when(emailVerificationRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(emailVerification));
    }

    private void prepareSuccessfulSignup() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        prepareVerifiedEmail();
        when(regionRepository.findById(123L)).thenReturn(Optional.of(activityRegion));
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertValidationError(SignupRequest request) {
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private void assertInvalidInterestCategories(List<PostingCategory> interestCategories) {
        assertThatThrownBy(
                        () ->
                                authService.signup(
                                        signupRequest(123L, "홍길동", "길동", interestCategories)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INTEREST_CATEGORY_COUNT));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private static SignupRequest signupRequest(Long activityRegionId) {
        return signupRequest(activityRegionId, "홍길동", "길동");
    }

    private static SignupRequest signupRequest(
            Long activityRegionId, String name, String nickname) {
        return signupRequest(activityRegionId, name, nickname, List.of(PostingCategory.WELFARE));
    }

    private static SignupRequest signupRequest(
            Long activityRegionId,
            String name,
            String nickname,
            List<PostingCategory> interestCategories) {
        return new SignupRequest(
                name,
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                "01012345678",
                "test@example.com",
                "password123!",
                "password123!",
                nickname,
                null,
                activityRegionId,
                interestCategories,
                true,
                true,
                false);
    }
}
