package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.domain.auth.dto.AccountRecoveryResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private final AccountRecoveryTransactionService transactionService;

    public AccountRecoveryResponse recoverEmail(AccountRecoveryRequest request) {
        AccountRecoveryTransactionResult result =
                transactionService.recoverEmail(request.phoneVerificationId());
        return switch (result.outcome()) {
            case EMAIL -> AccountRecoveryResponse.email(result.email());
            case KAKAO -> AccountRecoveryResponse.kakao();
            case ACCOUNT_NOT_FOUND -> throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        };
    }
}
