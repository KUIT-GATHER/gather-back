package com.gather.gather.domain.auth.kakao.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class KakaoAdminUnlinkResultTest {

    private static final Instant RETRY_AFTER_AT = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void staticFactory_createsRetryableResultWithRetryAfter() {
        assertThat(
                        KakaoAdminUnlinkResult.of(
                                KakaoAdminUnlinkDisposition.RETRYABLE, 429, -10, RETRY_AFTER_AT))
                .isEqualTo(
                        new KakaoAdminUnlinkResult(
                                KakaoAdminUnlinkDisposition.RETRYABLE, 429, -10, RETRY_AFTER_AT));
    }

    @Test
    void canonicalConstructor_allowsRetryableWithoutRetryAfter() {
        assertThat(
                        new KakaoAdminUnlinkResult(
                                KakaoAdminUnlinkDisposition.RETRYABLE, null, null, null))
                .extracting(KakaoAdminUnlinkResult::retryAfterAt)
                .isNull();
    }

    @Test
    void canonicalConstructor_allowsNonRetryableWithoutRetryAfter() {
        assertThat(new KakaoAdminUnlinkResult(KakaoAdminUnlinkDisposition.SUCCESS, 200, null, null))
                .extracting(KakaoAdminUnlinkResult::disposition)
                .isEqualTo(KakaoAdminUnlinkDisposition.SUCCESS);
    }

    @Test
    void canonicalConstructor_rejectsMissingDisposition() {
        assertThatThrownBy(() -> new KakaoAdminUnlinkResult(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disposition");
    }

    @Test
    void canonicalConstructor_rejectsRetryAfterForNonRetryableDisposition() {
        assertThatThrownBy(
                        () ->
                                new KakaoAdminUnlinkResult(
                                        KakaoAdminUnlinkDisposition.PERMANENT_REQUEST,
                                        400,
                                        -2,
                                        RETRY_AFTER_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Retry-After");
    }

    @Test
    void canonicalConstructor_rejectsRetryAfterOutside429Or5xx() {
        assertThatThrownBy(
                        () ->
                                new KakaoAdminUnlinkResult(
                                        KakaoAdminUnlinkDisposition.RETRYABLE,
                                        400,
                                        -1,
                                        RETRY_AFTER_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("429");
        assertThatThrownBy(
                        () ->
                                new KakaoAdminUnlinkResult(
                                        KakaoAdminUnlinkDisposition.RETRYABLE,
                                        null,
                                        null,
                                        RETRY_AFTER_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("429");
    }
}
