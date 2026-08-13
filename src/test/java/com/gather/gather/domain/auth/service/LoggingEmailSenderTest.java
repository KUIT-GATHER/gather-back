package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoggingEmailSenderTest {

    @Test
    void sendVerificationCode_masksEmailAndDoesNotExposeCode(CapturedOutput output) {
        String email = "person@example.com";
        String verificationCode = "123456";

        new LoggingEmailSender().sendVerificationCode(email, verificationCode);

        assertThat(output.getAll())
                .contains("p***@example.com")
                .doesNotContain(email, verificationCode);
    }
}
