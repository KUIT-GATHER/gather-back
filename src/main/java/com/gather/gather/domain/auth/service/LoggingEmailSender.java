package com.gather.gather.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("Email verification code for {}: {}", email, code);
    }
}
