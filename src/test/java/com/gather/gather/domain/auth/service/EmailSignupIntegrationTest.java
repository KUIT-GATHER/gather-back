package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.gather.gather.domain.user.dto.ProfileImageCurrentResponse;
import com.gather.gather.domain.user.service.ProfileImageService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailSignupIntegrationTest {

    private static final String EMAIL = "email-signup-integration@example.com";
    private static final String PHONE_NUMBER = "01095550001";

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProfileImageService profileImageService;

    private Region activityRegion;

    @BeforeEach
    void setUp() {
        activityRegion =
                regionRepository.save(
                        Region.create("email-signup-integration", 2, "email-signup-code", null));
        EmailVerification verification =
                EmailVerification.create(EMAIL, "123456", LocalDateTime.now().plusDays(1));
        verification.verify(LocalDateTime.now());
        emailVerificationRepository.save(verification);
    }

    @Test
    @DisplayName("이메일 회원가입은 해시된 Refresh Token을 저장하고 발급한 Access Token으로 보호 API에 접근한다")
    void signup_persistsHashedRefreshTokenAndIssuesUsableAccessToken() throws Exception {
        SignupResult result = authService.signup(signupRequest());

        User savedUser = userRepository.findByEmail(EMAIL).orElseThrow();
        String refreshTokenHash = tokenProvider.hashToken(result.refreshToken());
        RefreshToken savedRefreshToken =
                refreshTokenRepository.findByTokenHash(refreshTokenHash).orElseThrow();

        assertThat(savedRefreshToken.getTokenHash()).isEqualTo(refreshTokenHash);
        assertThat(savedRefreshToken.getTokenHash()).isNotEqualTo(result.refreshToken());
        assertThat(savedRefreshToken.getUser().getId()).isEqualTo(savedUser.getId());
        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().tokenType()).isEqualTo("Bearer");

        when(profileImageService.getCurrentProfileImage())
                .thenReturn(new ProfileImageCurrentResponse(null));
        mockMvc.perform(
                        get("/api/v1/users/me/profile-image")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + result.response().accessToken()))
                .andExpect(status().isOk());
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(
                "이메일회원",
                LocalDate.of(2001, 1, 1),
                Gender.FEMALE,
                PHONE_NUMBER,
                EMAIL,
                "password1",
                "password1",
                "자동로그인",
                null,
                activityRegion.getId(),
                List.of(PostingCategory.WELFARE),
                true,
                true,
                false);
    }
}
