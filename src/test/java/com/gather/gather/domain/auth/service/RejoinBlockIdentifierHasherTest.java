package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.config.RejoinBlockHmacProperties;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RejoinBlockIdentifierHasherTest {

    @Test
    void hashPhone_returnsSameHashForSameSecretAndInput() {
        RejoinBlockIdentifierHasher hasher = hasher("first-secret-value-with-at-least-32-bytes");

        RejoinBlockIdentifier first = hasher.hashPhone("01012345678");
        RejoinBlockIdentifier second = hasher.hashPhone("01012345678");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void hashPhone_normalizesDifferentPhoneNumberRepresentations() {
        RejoinBlockIdentifierHasher hasher = hasher("first-secret-value-with-at-least-32-bytes");

        RejoinBlockIdentifier hyphenated = hasher.hashPhone("010-1234-5678");
        RejoinBlockIdentifier spaced = hasher.hashPhone("010 1234 5678");

        assertThat(hyphenated).isEqualTo(spaced);
    }

    @Test
    void hashPhoneAndKakao_useDifferentNamespaces() {
        RejoinBlockIdentifierHasher hasher = hasher("first-secret-value-with-at-least-32-bytes");

        RejoinBlockIdentifier phone = hasher.hashPhone("1234567890");
        RejoinBlockIdentifier kakao = hasher.hashKakao("1234567890");

        assertThat(phone.type()).isEqualTo(AccountRejoinBlockIdentifierType.PHONE);
        assertThat(kakao.type()).isEqualTo(AccountRejoinBlockIdentifierType.KAKAO);
        assertThat(phone.hash()).isNotEqualTo(kakao.hash());
    }

    @Test
    void hashPhone_returnsDifferentHashForDifferentSecret() {
        RejoinBlockIdentifierHasher firstHasher =
                hasher("first-secret-value-with-at-least-32-bytes");
        RejoinBlockIdentifierHasher secondHasher =
                hasher("second-secret-value-with-at-least-32-bytes");

        assertThat(firstHasher.hashPhone("01012345678").hash())
                .isNotEqualTo(secondHasher.hashPhone("01012345678").hash());
    }

    @Test
    void hashResult_doesNotContainOriginalIdentifier() {
        RejoinBlockIdentifierHasher hasher = hasher("first-secret-value-with-at-least-32-bytes");
        String phoneNumber = "01012345678";

        RejoinBlockIdentifier result = hasher.hashPhone(phoneNumber);

        assertThat(result.hash()).hasSize(64).doesNotContain(phoneNumber);
    }

    @Test
    void properties_rejectInvalidBase64Secret() {
        assertThatThrownBy(() -> new RejoinBlockHmacProperties("not-base64!", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void properties_rejectSecretShorterThan32Bytes() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new RejoinBlockHmacProperties(shortSecret, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최소 32바이트");
    }

    private RejoinBlockIdentifierHasher hasher(String secret) {
        String encodedSecret =
                Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        RejoinBlockHmacProperties properties = new RejoinBlockHmacProperties(encodedSecret, 1);
        return new RejoinBlockIdentifierHasher(properties, new PhoneNumberNormalizer());
    }
}
