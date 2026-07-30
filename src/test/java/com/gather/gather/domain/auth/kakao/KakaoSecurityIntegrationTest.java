package com.gather.gather.domain.auth.kakao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenProvider;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.global.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 카카오 API의 Security 경계 검증. 카카오 API는 공개 경로지만 공개가 곧 무검증은 아니며, 가입 토큰과 일반 Access Token이 서로의 자리에서 통하지
 * 않아야 한다.
 *
 * <p>모든 케이스가 카카오 호출 이전(요청 검증·가입 토큰 검증) 단계에서 끝나므로 실제 네트워크를 타지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KakaoSecurityIntegrationTest {

    private static final String LOGIN_PATH = "/api/v1/auth/kakao/login";
    private static final String SIGNUP_PATH = "/api/v1/auth/kakao/signup";
    private static final String SECURED_PATH = "/test/secured";
    private static final String SIGNUP_TOKEN_HEADER = "X-Signup-Token";

    private static final String VALID_SIGNUP_BODY =
            """
            {
              "name": "홍길동",
              "birthDate": "2002-03-15",
              "gender": "MALE",
              "phoneNumber": "01012345678",
              "nickname": "길동",
              "introduction": null,
              "activityRegionId": 123,
              "interestCategories": ["WELFARE"],
              "serviceTermsAgreed": true,
              "privacyPolicyAgreed": true,
              "marketingAgreed": false
            }
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private SocialSignupTokenProvider socialSignupTokenProvider;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private SocialAccountProviderIdCipher providerIdCipher;
    @Autowired private JwtProperties jwtProperties;

    @Test
    @DisplayName("카카오 로그인은 Access Token 없이 접근할 수 있다")
    void kakaoLogin_withoutAccessToken_isNotUnauthorized() throws Exception {
        mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("카카오 가입은 Access Token 없이 접근할 수 있지만 가입 토큰이 없으면 완료할 수 없다")
    void kakaoSignup_withoutSignupToken_isRejectedBySignupTokenGate() throws Exception {
        mockMvc.perform(
                        post(SIGNUP_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_SIGNUP_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SIGNUP_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("가입용 토큰으로는 일반 보호 API에 접근할 수 없다")
    void signupToken_cannotAccessProtectedApi() throws Exception {
        String signupToken =
                socialSignupTokenProvider.createSignupToken(
                        SocialProvider.KAKAO,
                        identifierHasher.hashKakao("123456789"),
                        providerIdCipher.encrypt("123456789"));

        mockMvc.perform(
                        get(SECURED_PATH)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signupToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("일반 Access Token으로는 카카오 추가정보 가입을 할 수 없다")
    void accessToken_cannotBeUsedAsSignupToken() throws Exception {
        mockMvc.perform(
                        post(SIGNUP_PATH)
                                .header(SIGNUP_TOKEN_HEADER, accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_SIGNUP_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SIGNUP_TOKEN_INVALID"));
    }

    private String accessToken() {
        long now = System.currentTimeMillis();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
        return Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 1_800_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
