package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordResetTokenCodecTest {

    private final PasswordResetTokenCodec codec = new PasswordResetTokenCodec();

    @Test
    @DisplayName("32바이트 난수를 padding 없는 Base64URL 43자로 발급한다")
    void generateToken_returnsUrlSafe43CharactersOf32Bytes() {
        String token = codec.generateToken();

        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    @DisplayName("발급 토큰은 매번 달라진다")
    void generateToken_returnsDistinctTokens() {
        Set<String> tokens = new HashSet<>();
        IntStream.range(0, 200).forEach(index -> tokens.add(codec.generateToken()));

        assertThat(tokens).hasSize(200);
    }

    @Test
    @DisplayName("토큰 hash는 US-ASCII 바이트의 SHA-256을 64자 소문자 hex로 반환한다")
    void validateAndHash_returnsLowercaseHexSha256() throws Exception {
        String token = codec.generateToken();

        String hash = codec.validateAndHash(token);

        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(hash).isEqualTo(expectedSha256Hex(token));
    }

    @Test
    @DisplayName("같은 토큰은 같은 hash, 다른 토큰은 다른 hash를 만든다")
    void validateAndHash_isDeterministicPerToken() {
        String token = codec.generateToken();
        String otherToken = codec.generateToken();

        assertThat(codec.validateAndHash(token)).isEqualTo(codec.validateAndHash(token));
        assertThat(codec.validateAndHash(token)).isNotEqualTo(codec.validateAndHash(otherToken));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "   ",
                "AAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA "
            })
    @DisplayName("null·blank·길이 오류·허용되지 않는 문자는 DB 조회 전에 INVALID로 거부한다")
    void validateAndHash_rejectsMalformedToken(String token) {
        assertThatThrownBy(() -> codec.validateAndHash(token))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    @Test
    @DisplayName("형식만 맞으면 외부에서 받은 토큰도 hash로 변환한다")
    void validateAndHash_acceptsExternallySuppliedWellFormedToken() {
        String token = "A".repeat(43);

        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(codec.validateAndHash(token)).matches("^[0-9a-f]{64}$");
    }

    private String expectedSha256Hex(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.US_ASCII)));
    }
}
