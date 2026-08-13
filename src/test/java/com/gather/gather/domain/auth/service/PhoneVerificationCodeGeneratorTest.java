package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneVerificationCodeGeneratorTest {

    @Test
    @DisplayName("SecureRandom 기반 인증문구는 GATHER 접두사와 10자리 영숫자 난수로 생성된다")
    void generate_returnsExpectedFormatAndDistinctValues() {
        PhoneVerificationCodeGenerator generator = new PhoneVerificationCodeGenerator();
        Set<String> generated = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            generated.add(generator.generate());
        }

        assertThat(generated).hasSize(100).allMatch(code -> code.matches("^GATHER-[A-Z2-9]{10}$"));
    }
}
