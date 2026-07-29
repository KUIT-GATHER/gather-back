package com.gather.gather.domain.auth.service;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

    public String normalize(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = phoneNumber.replaceAll("[\\s-]", "");
        if (!normalized.matches("^[0-9]+$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }
}
