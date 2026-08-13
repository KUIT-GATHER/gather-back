package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
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
public class EmailVerificationRequirementService {

    public static final int VERIFIED_RESULT_VALIDITY_MINUTES = 30;

    private final EmailVerificationRepository emailVerificationRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeForSignup(String email, UUID verificationId) {
        if (verificationId == null) {
            throw required();
        }
        // 미존재 email에 대한 불필요한 gap lock을 줄이기 위한 fast guard다.
        // 실제 검증은 아래 locking read가 반환한 최신 row 상태를 기준으로 수행한다.
        if (!emailVerificationRepository.existsByEmail(email)) {
            throw required();
        }
        EmailVerification verification =
                emailVerificationRepository
                        .findByEmailForUpdate(email)
                        .orElseThrow(EmailVerificationRequirementService::required);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!verification.getEmail().equals(email)
                || !verification.getVerificationId().equals(verificationId.toString())
                || !verification.isVerified()
                || verification.isVerifiedResultExpired(now, VERIFIED_RESULT_VALIDITY_MINUTES)
                || verification.isConsumed()) {
            throw required();
        }
        verification.consume(now);
    }

    private static BusinessException required() {
        return new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
    }
}
