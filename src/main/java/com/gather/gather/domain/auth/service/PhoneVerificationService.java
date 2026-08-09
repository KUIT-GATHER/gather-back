package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.config.PhoneVerificationProperties;
import com.gather.gather.domain.auth.dto.PhoneVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationQrCodeResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationStartRequest;
import com.gather.gather.domain.auth.dto.PhoneVerificationStartResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationStatus;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.octomo.client.OctomoApiClient;
import com.gather.gather.domain.auth.octomo.config.OctomoProperties;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final int REQUEST_VALIDITY_MINUTES = 5;
    private static final int OCTOMO_LOOKUP_MINUTES = 5;

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final PhoneNumberPolicy phoneNumberPolicy;
    private final PhoneVerificationCodeGenerator codeGenerator;
    private final PhoneVerificationTransactionService transactionService;
    private final OctomoApiClient octomoApiClient;
    private final OctomoProperties octomoProperties;
    private final PhoneVerificationProperties properties;
    private final Clock clock;

    @Transactional
    public PhoneVerificationStartResponse start(PhoneVerificationStartRequest request) {
        String phoneNumber = phoneNumberPolicy.normalize(request.phoneNumber());
        LocalDateTime now = LocalDateTime.now(clock);
        phoneVerificationRepository
                .findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(
                        latest ->
                                latest.getCreatedAt().plus(properties.startCooldown()).isAfter(now))
                .ifPresent(
                        ignored -> {
                            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
                        });
        LocalDateTime expiresAt = now.plusMinutes(REQUEST_VALIDITY_MINUTES);
        String verificationId = UUID.randomUUID().toString();
        String verificationCode = codeGenerator.generate();
        PhoneVerification verification =
                PhoneVerification.create(
                        verificationId, phoneNumber, verificationCode, expiresAt, now);
        phoneVerificationRepository.save(verification);
        return new PhoneVerificationStartResponse(
                verificationId,
                octomoProperties.receiverNumber(),
                verificationCode,
                expiresAt.toInstant(ZoneOffset.UTC));
    }

    public PhoneVerificationQrCodeResponse createQrCode(String verificationId) {
        return new PhoneVerificationQrCodeResponse(
                octomoApiClient.createQrCode(transactionService.reserveQr(verificationId)));
    }

    public PhoneVerificationConfirmResponse confirm(String verificationId) {
        PhoneVerificationConfirmReservation reservation =
                transactionService.reserveConfirm(verificationId);
        if (reservation.alreadyVerified()) {
            return new PhoneVerificationConfirmResponse(PhoneVerificationStatus.VERIFIED);
        }

        boolean exists =
                octomoApiClient.existsMessage(
                        reservation.phoneNumber(),
                        reservation.verificationCode(),
                        OCTOMO_LOOKUP_MINUTES);
        if (!exists) {
            return new PhoneVerificationConfirmResponse(PhoneVerificationStatus.PENDING);
        }
        return new PhoneVerificationConfirmResponse(transactionService.verify(verificationId));
    }
}
