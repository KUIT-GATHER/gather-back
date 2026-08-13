package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneVerificationSmsMessageFormatterTest {

    private final PhoneVerificationSmsMessageFormatter formatter =
            new PhoneVerificationSmsMessageFormatter();

    @Test
    @DisplayName("인증코드를 줄바꿈과 공백이 고정된 전체 SMS 본문으로 만든다")
    void format_returnsExactSmsMessageWithoutTrailingWhitespace() {
        String message = formatter.format("GATHER-A1B2C3D4E5");

        assertThat(message)
                .isEqualTo(
                        "[Gather]\n"
                                + "전화번호 인증을 위한 문자입니다.\n"
                                + "본 문자를 전송하시면 전화번호 인증이 자동으로 완료됩니다.\n\n"
                                + "인증코드: [GATHER-A1B2C3D4E5]")
                .doesNotStartWith(" ")
                .doesNotEndWith(" ")
                .doesNotEndWith("\n")
                .doesNotContain("\r");
    }
}
