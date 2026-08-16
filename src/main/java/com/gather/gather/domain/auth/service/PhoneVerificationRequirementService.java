package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationRequirementService {

    public static final int VERIFIED_RESULT_VALIDITY_MINUTES = 30;

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeForSignup(UUID verificationId, String phoneNumber) {
        if (verificationId == null) {
            throw required();
        }
        PhoneVerification verification =
                phoneVerificationRepository
                        .findByVerificationIdForUpdate(verificationId.toString())
                        .orElseThrow(PhoneVerificationRequirementService::required);
        LocalDateTime now = LocalDateTime.now(clock);
        if (verification.getPurpose() != PhoneVerificationPurpose.SIGNUP) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_PURPOSE_MISMATCH);
        }
        if (!verification.isVerified()
                || !verification.getPhoneNumber().equals(phoneNumber)
                || verification.isVerifiedResultExpired(now, VERIFIED_RESULT_VALIDITY_MINUTES)
                || verification.isConsumed()) {
            throw required();
        }
        verification.consume(now);
    }

    private static BusinessException required() {
        return new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
    }
}
