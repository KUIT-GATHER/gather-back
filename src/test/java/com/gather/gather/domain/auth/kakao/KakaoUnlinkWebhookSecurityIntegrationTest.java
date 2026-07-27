package com.gather.gather.domain.auth.kakao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 연결 해제 웹훅의 Security 경계 검증.
 *
 * <p>JWT 없이 접근할 수 있어야 하면서(카카오는 우리 토큰을 갖고 있지 않다) 동시에 어드민 키로 발신자를 걸러야 하는 엔드포인트다. 설정 실수 하나가 인증 없이 계정을
 * 종료시킬 수 있는 구멍이 되므로 단위 테스트로 대체할 수 없다.
 *
 * <p>존재하지 않는 회원번호를 보내 모든 케이스가 no-op으로 끝나게 한다 — 검증 대상은 접근 제어지 종료 처리가 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KakaoUnlinkWebhookSecurityIntegrationTest {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/kakao/unlink";
    // src/test/resources/application.yml의 더미 값과 같아야 한다.
    private static final String ADMIN_KEY = "test-kakao-admin-key-0123456789a";
    private static final String APP_ID = "1234567";
    private static final String UNKNOWN_KAKAO_USER_ID = "999999999999";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("어드민 키가 맞으면 JWT 없이도 200이다")
    void webhook_withValidAdminKey_returns200WithoutJwt() throws Exception {
        mockMvc.perform(
                        post(WEBHOOK_PATH)
                                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("app_id", APP_ID)
                                .param("user_id", UNKNOWN_KAKAO_USER_ID)
                                .param("referrer_type", "UNLINK_FROM_APPS"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET으로 와도 동일하게 200이다 (카카오 문서에 두 방식이 모두 있다)")
    void webhook_withGetMethod_returns200() throws Exception {
        mockMvc.perform(
                        get(WEBHOOK_PATH)
                                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY)
                                .param("app_id", APP_ID)
                                .param("user_id", UNKNOWN_KAKAO_USER_ID)
                                .param("referrer_type", "UNLINK_FROM_APPS"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("어드민 키가 없으면 401이다")
    void webhook_withoutAdminKey_returns401() throws Exception {
        mockMvc.perform(
                        post(WEBHOOK_PATH)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("app_id", APP_ID)
                                .param("user_id", UNKNOWN_KAKAO_USER_ID)
                                .param("referrer_type", "UNLINK_FROM_APPS"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("어드민 키가 틀리면 401이다")
    void webhook_withWrongAdminKey_returns401() throws Exception {
        mockMvc.perform(
                        post(WEBHOOK_PATH)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "KakaoAK wrong-admin-key-01234567890")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("app_id", APP_ID)
                                .param("user_id", UNKNOWN_KAKAO_USER_ID)
                                .param("referrer_type", "UNLINK_FROM_APPS"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("알 수 없는 referrer_type이 와도 400으로 죽지 않는다")
    void webhook_withUnknownReferrerType_returns200() throws Exception {
        mockMvc.perform(
                        post(WEBHOOK_PATH)
                                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("app_id", APP_ID)
                                .param("user_id", UNKNOWN_KAKAO_USER_ID)
                                .param("referrer_type", "SOMETHING_KAKAO_ADDED_LATER"))
                .andExpect(status().isOk());
    }
}
