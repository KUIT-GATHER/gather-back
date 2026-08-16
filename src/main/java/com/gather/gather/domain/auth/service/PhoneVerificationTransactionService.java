package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.config.PhoneVerificationProperties;
import com.gather.gather.domain.auth.dto.PhoneVerificationStatus;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationTransactionService {

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final SignupValidator signupValidator;
    private final AccountRejoinBlockService accountRejoinBlockService;
    private final PhoneVerificationProperties properties;
    private final Clock clock;

    @Transactional
    public PhoneVerificationConfirmReservation reserveConfirm(String verificationId) {
        PhoneVerification verification = lockVerification(verificationId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (verification.isVerified()) {
            if (verification.isVerifiedResultExpired(
                    now, PhoneVerificationRequirementService.VERIFIED_RESULT_VALIDITY_MINUTES)) {
                throw new BusinessException(ErrorCode.PHONE_VERIFICATION_EXPIRED);
            }
            return PhoneVerificationConfirmReservation.verified();
        }
        requireActive(verification, now);
        if (!verification.canReserveConfirm(
                now, properties.confirmCooldown(), properties.confirmMaxAttempts())) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
        }
        verification.reserveConfirm(now);
        return PhoneVerificationConfirmReservation.reserved(
                verification.getPhoneNumber(), verification.getVerificationCode());
    }

    @Transactional
    public String reserveQr(String verificationId) {
        PhoneVerification verification = lockVerification(verificationId);
        LocalDateTime now = LocalDateTime.now(clock);
        requireActive(verification, now);
        if (!verification.canReserveQr(now, properties.qrCooldown(), properties.qrMaxAttempts())) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
        }
        verification.reserveQr(now);
        return verification.getVerificationCode();
    }

    @Transactional
    public PhoneVerificationStatus verify(String verificationId) {
        PhoneVerification verification = lockVerification(verificationId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (verification.isVerified()) {
            return PhoneVerificationStatus.VERIFIED;
        }
        requireActive(verification, now);
        switch (verification.getPurpose()) {
            case SIGNUP -> {
                signupValidator.validatePhoneNumberNotDuplicated(verification.getPhoneNumber());
                if (accountRejoinBlockService.isPhoneBlocked(verification.getPhoneNumber(), now)) {
                    throw new BusinessException(ErrorCode.ACCOUNT_REJOIN_BLOCKED);
                }
            }
            case FIND_ACCOUNT, RESET_PASSWORD -> {
                // 복구 목적에서는 계정 존재 여부를 confirm 단계에서 판정하거나 노출하지 않는다.
            }
        }
        verification.verify(now);
        return PhoneVerificationStatus.VERIFIED;
    }

    private PhoneVerification lockVerification(String verificationId) {
        return phoneVerificationRepository
                .findByVerificationIdForUpdate(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_VERIFICATION_NOT_FOUND));
    }

    private void requireActive(PhoneVerification verification, LocalDateTime now) {
        if (verification.isExpired(now)) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_EXPIRED);
        }
    }
}
