package com.gather.gather.domain.auth.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class PhoneVerificationCodeGenerator {

    private static final String PREFIX = "GATHER-";
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int RANDOM_LENGTH = 10;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int index = 0; index < RANDOM_LENGTH; index++) {
            code.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
