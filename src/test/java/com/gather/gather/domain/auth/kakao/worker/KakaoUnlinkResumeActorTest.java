package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KakaoUnlinkResumeActorTest {

    @Test
    void normalize_trimsAndAcceptsSharedActorFormat() {
        assertThat(KakaoUnlinkResumeActor.normalize(" operator.name@example-1 "))
                .isEqualTo("operator.name@example-1");
        assertThat(KakaoUnlinkResumeActor.normalize("a".repeat(KakaoUnlinkResumeActor.MAX_LENGTH)))
                .hasSize(KakaoUnlinkResumeActor.MAX_LENGTH);
    }

    @ParameterizedTest
    @MethodSource("invalidActors")
    void normalize_rejectsInvalidActor(String actor) {
        assertThatThrownBy(() -> KakaoUnlinkResumeActor.normalize(actor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidActors() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(" "),
                Arguments.of("operator name"),
                Arguments.of("operator/1"),
                Arguments.of("a".repeat(KakaoUnlinkResumeActor.MAX_LENGTH + 1)));
    }
}
