package com.gather.gather.domain.auth.service;

import org.springframework.stereotype.Component;

@Component
public class PhoneVerificationSmsMessageFormatter {

    public String format(String verificationCode) {
        return String.join(
                "\n",
                "[Gather]",
                "전화번호 인증을 위한 문자입니다.",
                "본 문자를 전송하시면 전화번호 인증이 자동으로 완료됩니다.",
                "",
                "인증코드: [" + verificationCode + "]");
    }
}
