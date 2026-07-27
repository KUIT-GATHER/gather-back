package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.client.KakaoApiClient;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 API 전체 플로우 검증. 상태 전이는 단위 테스트로 덮이지만, 여러 기기 로그인으로 쌓인 Refresh Token이 실제 DB에서 전부 지워지는지와 인증 필터를 통과한
 * 요청이 쿠키 만료 헤더를 받는지는 실제 컨텍스트가 있어야 확인할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
class AccountWithdrawalIntegrationTest {

    private static final String WITHDRAWAL_PATH = "/api/v1/users/me";

    // 테스트 사용자는 카카오 연동이 없어 호출될 일이 없지만, 연동이 있는 픽스처가 추가되는 순간 실제 카카오로 나가는 것을 막는다.
    @MockitoBean private KakaoApiClient kakaoApiClient;

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplicationEvents events;

    @Test
    @DisplayName("탈퇴하면 상태·사유가 기록되고 기기별 Refresh Token이 전부 삭제된다")
    void withdraw_terminatesAccountAndDeletesEveryRefreshToken() throws Exception {
        User user = userRepository.save(newUser());
        refreshTokenRepository.save(refreshToken(user, "device-a"));
        refreshTokenRepository.saveAndFlush(refreshToken(user, "device-b"));

        mockMvc.perform(
                        delete(WITHDRAWAL_PATH)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + tokenProvider.createAccessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        User withdrawn = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();

        assertThat(refreshTokenRepository.findByTokenHash(tokenHash(user, "device-a"))).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(tokenHash(user, "device-b"))).isEmpty();
    }

    @Test
    @DisplayName("탈퇴하면 도메인 정리를 위한 UserWithdrawnEvent가 한 번 발행된다")
    void withdraw_publishesUserWithdrawnEvent() throws Exception {
        User user = userRepository.saveAndFlush(newUser());

        mockMvc.perform(
                        delete(WITHDRAWAL_PATH)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + tokenProvider.createAccessToken(user)))
                .andExpect(status().isOk());

        assertThat(events.stream(UserWithdrawnEvent.class))
                .singleElement()
                .extracting(UserWithdrawnEvent::userId)
                .isEqualTo(user.getId());
    }

    @Test
    @DisplayName("토큰이 없으면 탈퇴 요청은 401이다")
    void withdraw_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete(WITHDRAWAL_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private User newUser() {
        Region region =
                regionRepository.save(
                        Region.create("테스트구", 2, "999" + (System.nanoTime() % 10000000L), null));
        return User.create(
                "탈퇴자",
                LocalDate.of(1995, 1, 1),
                Gender.MALE,
                "010" + System.nanoTime() % 100000000L,
                null,
                null,
                "wdtest" + (System.nanoTime() % 10_000_000L),
                null,
                true,
                true,
                false,
                region,
                List.of());
    }

    private RefreshToken refreshToken(User user, String device) {
        return RefreshToken.create(tokenHash(user, device), user, LocalDateTime.now().plusDays(14));
    }

    private String tokenHash(User user, String device) {
        return "hash-" + user.getId() + "-" + device;
    }
}
