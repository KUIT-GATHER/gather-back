package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.repository.AccountIdentityGuardRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountIdentityGuardService {

    private final AccountIdentityGuardRepository guardRepository;
    private final RejoinBlockIdentifierHasher identifierHasher;

    @Transactional(propagation = Propagation.MANDATORY)
    public RejoinBlockIdentifier lockPhone(String phoneNumber, LocalDateTime now) {
        RejoinBlockIdentifier identifier = identifierHasher.hashPhone(phoneNumber);
        if (identifier.type() != AccountRejoinBlockIdentifierType.PHONE) {
            throw new IllegalArgumentException("PHONE guard에는 전화번호 식별자만 사용할 수 있습니다.");
        }
        LocalDateTime requiredNow = Objects.requireNonNull(now, "guard 생성 시각은 필수입니다.");
        guardRepository.upsert(
                identifier.type().name(), identifier.hash(), identifier.keyVersion(), requiredNow);
        guardRepository
                .findByIdentityForUpdate(
                        identifier.type(), identifier.hash(), identifier.keyVersion())
                .orElseThrow(() -> new IllegalStateException("PHONE identity guard를 잠글 수 없습니다."));
        return identifier;
    }
}
