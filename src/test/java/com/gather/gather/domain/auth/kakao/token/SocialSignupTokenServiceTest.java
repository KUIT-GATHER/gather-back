package com.gather.gather.domain.auth.kakao.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SocialSignupTokenServiceTest {

    private final SocialSignupTokenService service = new SocialSignupTokenService();

    @Test
    @DisplayName("가입 토큰은 32바이트를 URL-safe Base64로 인코딩한 43자 문자열이다")
    void generateToken_returns256BitUrlSafeToken() {
        String token = service.generateToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("가입 토큰은 매번 새 난수로 발급된다")
    void generateToken_returnsDifferentTokens() {
        Set<String> tokens = new HashSet<>();

        for (int count = 0; count < 100; count++) {
            tokens.add(service.generateToken());
        }

        assertThat(tokens).hasSize(100);
    }

    @Test
    @DisplayName("동일 토큰의 SHA-256 hash는 결정적인 lowercase hex 64자다")
    void validateAndHash_returnsDeterministicLowercaseSha256Hex() {
        String token = service.generateToken();

        String first = service.validateAndHash(token);
        String second = service.validateAndHash(token);

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}").isNotEqualTo(token);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "short",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    @DisplayName("null·길이 오류·URL-safe Base64가 아닌 토큰은 원문을 노출하지 않고 거부한다")
    void validateAndHash_rejectsInvalidTokenWithoutExposingValue(String token) {
        assertThatThrownBy(() -> service.validateAndHash(token))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(ErrorCode.SIGNUP_TOKEN_INVALID))
                .hasMessage(ErrorCode.SIGNUP_TOKEN_INVALID.getMessage())
                .satisfies(
                        exception -> {
                            if (token != null && !token.isBlank()) {
                                assertThat(exception.getMessage()).doesNotContain(token);
                            }
                        });
    }

    @Test
    @DisplayName("기존 JWT 가입 토큰은 opaque token 형식으로 허용하지 않는다")
    void validateAndHash_rejectsLegacyJwtSignupToken() {
        String legacyJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature";

        assertThatThrownBy(() -> service.validateAndHash(legacyJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SIGNUP_TOKEN_INVALID);
    }
}
