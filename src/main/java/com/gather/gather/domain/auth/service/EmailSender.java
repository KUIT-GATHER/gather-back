package com.gather.gather.domain.auth.service;

public interface EmailSender {

    void sendVerificationCode(String email, String code);
}
