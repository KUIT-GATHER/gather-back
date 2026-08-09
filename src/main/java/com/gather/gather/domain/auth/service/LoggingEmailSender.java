package com.gather.gather.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "gather.email.mode", havingValue = "log")
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info(
                "Email verification delivery skipped in log mode: email={}",
                EmailMasker.mask(email));
    }
}
