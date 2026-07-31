package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.service.AccountRejoinBlockService;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SocialSignupSessionPersistenceService {

    private final SocialSignupSessionRepository sessionRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final AccountRejoinBlockService rejoinBlockService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNewAttempt(SocialSignupSession session, LocalDateTime now) {
        sessionRepository.saveAndFlush(session);
        socialAccountRepository
                .findByProviderAndProviderUserKeyForUpdate(
                        session.getProvider(), session.getProviderUserKey())
                .ifPresent(
                        account -> {
                            if (account.isLinked()) {
                                throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
                            }
                            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
                        });
        RejoinBlockIdentifier identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO,
                        session.getProviderUserKey(),
                        session.getProviderUserKeyVersion());
        if (rejoinBlockService.isBlocked(identifier, now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_REJOIN_BLOCKED);
        }
    }
}
