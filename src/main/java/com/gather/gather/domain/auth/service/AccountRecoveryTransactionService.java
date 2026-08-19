package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRecoveryTransactionService {

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final UserRepository userRepository;
    private final AccountLoginTypeResolver accountLoginTypeResolver;
    private final Clock clock;

    @Transactional
    public AccountRecoveryTransactionResult recoverEmail(UUID phoneVerificationId) {
        PhoneVerification verification = lockVerification(phoneVerificationId);
        LocalDateTime now = LocalDateTime.now(clock);
        validateForAccountRecovery(verification, now);

        Optional<User> user =
                userRepository.findByPhoneNumberForUpdate(verification.getPhoneNumber());
        AccountRecoveryTransactionResult result = classify(user);
        // 계정 유무를 확인한 정상 복구 시도는 결과 열거와 인증 재사용을 막기 위해 모두 소비한다.
        verification.consume(now);
        return result;
    }

    private PhoneVerification lockVerification(UUID phoneVerificationId) {
        if (phoneVerificationId == null) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        return phoneVerificationRepository
                .findByVerificationIdForUpdate(phoneVerificationId.toString())
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_VERIFICATION_NOT_FOUND));
    }

    private void validateForAccountRecovery(PhoneVerification verification, LocalDateTime now) {
        if (verification.getPurpose() != PhoneVerificationPurpose.FIND_ACCOUNT) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_PURPOSE_MISMATCH);
        }
        if (!verification.isVerified()) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        if (verification.isVerifiedResultExpired(
                now, PhoneVerificationRequirementService.VERIFIED_RESULT_VALIDITY_MINUTES)) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_EXPIRED);
        }
        if (verification.isConsumed()) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
    }

    private AccountRecoveryTransactionResult classify(Optional<User> optionalUser) {
        if (optionalUser.isEmpty()) {
            return AccountRecoveryTransactionResult.accountNotFound();
        }
        User user = optionalUser.get();
        Optional<AccountLoginType> loginType =
                accountLoginTypeResolver.resolveForActiveAccount(user);
        if (loginType.isEmpty()) {
            if (user.getStatus() == UserStatus.ACTIVE) {
                log.error("계정 복구 중 로그인 credential 불일치를 감지했습니다: userId={}", user.getId());
            }
            return AccountRecoveryTransactionResult.accountNotFound();
        }
        return switch (loginType.get()) {
            case EMAIL -> AccountRecoveryTransactionResult.email(user.getEmail());
            case KAKAO -> AccountRecoveryTransactionResult.kakao();
        };
    }
}
