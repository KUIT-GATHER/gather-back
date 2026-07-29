package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SocialSignupSessionRollbackIntegrationTest {

    private static final String PROVIDER_USER_ID = "signup-refresh-failure-20260730";

    @Autowired private KakaoSignupTransactionService signupTransactionService;
    @Autowired private SocialSignupSessionService signupSessionService;
    @Autowired private SocialSignupTokenService signupTokenService;
    @Autowired private SocialSignupSessionRepository signupSessionRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private SocialAccountProviderIdCipher providerIdCipher;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private RefreshTokenRepository refreshTokenRepository;

    private RejoinBlockIdentifier identifier;
    private String token;

    @BeforeEach
    void setUp() {
        identifier = identifierHasher.hashKakao(PROVIDER_USER_ID);
        token =
                signupSessionService.issue(
                        SocialProvider.KAKAO,
                        identifier,
                        providerIdCipher.encrypt(PROVIDER_USER_ID));
    }

    @AfterEach
    void tearDown() {
        signupSessionRepository
                .findByTokenHash(signupTokenService.hashToken(token))
                .ifPresent(signupSessionRepository::delete);
    }

    @Test
    @DisplayName("Refresh Token 저장 실패는 User·SocialAccount·세션 상태를 함께 rollback한다")
    void refreshTokenSaveFailure_rollsBackEntireSignup() {
        long userCount = databaseCount("users");
        long accountCount = databaseCount("social_account");
        long refreshCount = databaseCount("refresh_token");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenThrow(new DataIntegrityViolationException("forced refresh token failure"));
        User user =
                User.createSocial(
                        "홍길동",
                        LocalDate.of(2002, 3, 15),
                        Gender.MALE,
                        "01093999999",
                        "refreshrollback",
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of(PostingCategory.WELFARE));

        assertThatThrownBy(
                        () ->
                                signupTransactionService.createAccount(
                                        user, token, user.getPhoneNumber(), user.getNickname()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(databaseCount("users")).isEqualTo(userCount);
        assertThat(databaseCount("social_account")).isEqualTo(accountCount);
        assertThat(databaseCount("refresh_token")).isEqualTo(refreshCount);
        assertThat(
                        socialAccountRepository.findByProviderAndProviderUserKey(
                                SocialProvider.KAKAO, identifier.hash()))
                .isEmpty();
        SocialSignupSession session =
                signupSessionRepository
                        .findByTokenHash(signupTokenService.hashToken(token))
                        .orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
    }

    private long databaseCount(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
