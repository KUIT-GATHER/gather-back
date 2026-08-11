package com.gather.gather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatherApplicationTimeZoneTest {

    @Test
    @DisplayName("UTC와 동등한 JVM timezone은 허용한다")
    void validateJvmTimeZone_acceptsUtcEquivalentZones() {
        assertThatCode(() -> GatherApplication.validateJvmTimeZone(ZoneOffset.UTC))
                .doesNotThrowAnyException();
        assertThatCode(() -> GatherApplication.validateJvmTimeZone(ZoneId.of("UTC")))
                .doesNotThrowAnyException();
        assertThatCode(() -> GatherApplication.validateJvmTimeZone(ZoneId.of("Etc/UTC")))
                .doesNotThrowAnyException();
        assertThatCode(() -> GatherApplication.validateJvmTimeZone(ZoneId.of("GMT")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UTC가 아닌 JVM timezone은 거부한다")
    void validateJvmTimeZone_rejectsNonUtcZone() {
        assertThatThrownBy(() -> GatherApplication.validateJvmTimeZone(ZoneId.of("Asia/Seoul")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JVM default time zone must be UTC");
    }

    @Test
    @DisplayName("Gradle Test JVM timezone은 UTC이다")
    void testJvmUsesUtc() {
        assertThat(ZoneId.systemDefault().normalized()).isEqualTo(ZoneOffset.UTC);
    }
}
