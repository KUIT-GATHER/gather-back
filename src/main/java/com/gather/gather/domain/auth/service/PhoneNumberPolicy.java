package com.gather.gather.domain.auth.service;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberPolicy {

    private static final Pattern REMOVABLE_SEPARATOR_PATTERN =
            Pattern.compile("[\\p{javaWhitespace}\\p{Zs}-]");
    private static final Pattern MOBILE_NUMBER_PATTERN = Pattern.compile("^010[0-9]{8}$");

    public String normalize(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = REMOVABLE_SEPARATOR_PATTERN.matcher(phoneNumber).replaceAll("");
        if (!MOBILE_NUMBER_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }
}
