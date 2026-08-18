package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.config.EmailVerificationHmacProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailVerificationCodeHasherTest {

    private static final String SECRET =
            "Z2F0aGVyLXVuaXQtdGVzdC1lbWFpbC12ZXJpZmljYXRpb24taG1hYy1zZWNyZXQ=";
    private static final String VERIFICATION_ID = "11111111-2222-3333-4444-555555555555";
    private static final String CODE = "123456";

    // openssl dgst -sha256 -mac HMAC로 독립 계산한 값. 구현이 바뀌어도 저장 포맷이 유지되는지 고정한다.
    private static final String KNOWN_HASH =
            "10ad5b5fa54788f0ff5e9041bdad474d2843f6989ea06d15c313a01b33df944b";

    private final EmailVerificationCodeHasher hasher =
            new EmailVerificationCodeHasher(new EmailVerificationHmacProperties(SECRET));

    @Test
    @DisplayName("정해진 시크릿과 메시지에 대해 독립 계산한 HMAC 값과 일치한다")
    void hash_matchesKnownVector() {
        assertThat(hasher.hash(VERIFICATION_ID, CODE)).isEqualTo(KNOWN_HASH);
    }

    @Test
    @DisplayName("같은 인증 ID와 코드는 항상 같은 해시를 만든다")
    void hash_sameInput_isDeterministic() {
        assertThat(hasher.hash(VERIFICATION_ID, CODE))
                .isEqualTo(hasher.hash(VERIFICATION_ID, CODE));
    }

    @Test
    @DisplayName("코드가 다르면 해시가 달라진다")
    void hash_differentCode_producesDifferentHash() {
        assertThat(hasher.hash(VERIFICATION_ID, CODE))
                .isNotEqualTo(hasher.hash(VERIFICATION_ID, "654321"));
    }

    @Test
    @DisplayName("인증 ID가 다르면 같은 코드라도 해시가 달라진다")
    void hash_differentVerificationId_producesDifferentHash() {
        assertThat(hasher.hash(VERIFICATION_ID, CODE))
                .isNotEqualTo(hasher.hash("99999999-8888-7777-6666-555555555555", CODE));
    }

    @Test
    @DisplayName("시크릿이 다르면 같은 메시지라도 해시가 달라진다")
    void hash_differentSecret_producesDifferentHash() {
        EmailVerificationCodeHasher other =
                new EmailVerificationCodeHasher(
                        new EmailVerificationHmacProperties(
                                "YW5vdGhlci1lbWFpbC12ZXJpZmljYXRpb24taG1hYy1zZWNyZXQtdmFsdWU="));

        assertThat(hasher.hash(VERIFICATION_ID, CODE))
                .isNotEqualTo(other.hash(VERIFICATION_ID, CODE));
    }

    @Test
    @DisplayName("해시는 소문자 16진수 64자리다")
    void hash_isLowercaseHexOf64Characters() {
        assertThat(hasher.hash(VERIFICATION_ID, CODE)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("올바른 코드는 저장된 해시와 일치한다")
    void verify_correctCode_returnsTrue() {
        assertThat(hasher.verify(VERIFICATION_ID, CODE, KNOWN_HASH)).isTrue();
    }

    @Test
    @DisplayName("틀린 코드는 검증에 실패한다")
    void verify_wrongCode_returnsFalse() {
        assertThat(hasher.verify(VERIFICATION_ID, "000000", KNOWN_HASH)).isFalse();
    }

    @Test
    @DisplayName("다른 인증 ID의 해시는 재사용할 수 없다")
    void verify_otherVerificationIdHash_returnsFalse() {
        String otherHash = hasher.hash("99999999-8888-7777-6666-555555555555", CODE);

        assertThat(hasher.verify(VERIFICATION_ID, CODE, otherHash)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "   ",
                "not-a-hash",
                "10AD5B5FA54788F0FF5E9041BDAD474D2843F6989EA06D15C313A01B33DF944B",
                "10ad5b5fa54788f0ff5e9041bdad474d2843f6989ea06d15c313a01b33df944",
                "10ad5b5fa54788f0ff5e9041bdad474d2843f6989ea06d15c313a01b33df944bb",
                "10ad5b5fa54788f0ff5e9041bdad474d2843f6989ea06d15c313a01b33df944g"
            })
    @DisplayName("저장된 해시가 없거나 형식이 깨졌으면 인증을 통과시키지 않는다")
    void verify_malformedStoredHash_returnsFalse(String storedCodeHash) {
        assertThat(hasher.verify(VERIFICATION_ID, CODE, storedCodeHash)).isFalse();
        assertThat(EmailVerificationCodeHasher.isStoredCodeHashFormatValid(storedCodeHash))
                .isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("입력 코드가 비어 있으면 검증에 실패한다")
    void verify_blankCode_returnsFalse(String rawCode) {
        assertThat(hasher.verify(VERIFICATION_ID, rawCode, KNOWN_HASH)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("인증 ID 없이 해시를 만들 수 없다")
    void hash_blankVerificationId_throws(String verificationId) {
        assertThatThrownBy(() -> hasher.hash(verificationId, CODE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("코드 없이 해시를 만들 수 없다")
    void hash_blankCode_throws(String rawCode) {
        assertThatThrownBy(() -> hasher.hash(VERIFICATION_ID, rawCode))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
