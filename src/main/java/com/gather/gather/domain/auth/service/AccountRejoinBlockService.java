package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountRejoinBlockService {

    private static final long REJOIN_BLOCK_DAYS = 7;

    private final AccountRejoinBlockRepository blockRepository;
    private final RejoinBlockIdentifierHasher identifierHasher;

    @Transactional(readOnly = true)
    public boolean isPhoneBlocked(String phoneNumber, LocalDateTime now) {
        LocalDateTime requiredNow = requireNow(now);
        return isBlocked(identifierHasher.hashPhone(phoneNumber), requiredNow);
    }

    @Transactional(readOnly = true)
    public boolean isKakaoBlocked(String providerUserId, LocalDateTime now) {
        LocalDateTime requiredNow = requireNow(now);
        return isBlocked(identifierHasher.hashKakao(providerUserId), requiredNow);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(RejoinBlockIdentifier identifier, LocalDateTime now) {
        RejoinBlockIdentifier requiredIdentifier = requireIdentifier(identifier);
        return blockRepository.existsByIdentifierTypeAndIdentifierHashAndExpiresAtAfter(
                requiredIdentifier.type(), requiredIdentifier.hash(), requireNow(now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isBlockedForUpdate(RejoinBlockIdentifier identifier, LocalDateTime now) {
        RejoinBlockIdentifier requiredIdentifier = requireIdentifier(identifier);
        LocalDateTime requiredNow = requireNow(now);
        return blockRepository
                .findByIdentifierForUpdate(requiredIdentifier.type(), requiredIdentifier.hash())
                .map(block -> block.isActiveAt(requiredNow))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createOrExtendBlock(
            RejoinBlockIdentifier identifier, Long sourceUserId, LocalDateTime now) {
        upsert(requireIdentifier(identifier), requireSourceUserId(sourceUserId), requireNow(now));
    }

    private void upsert(RejoinBlockIdentifier identifier, Long sourceUserId, LocalDateTime now) {
        blockRepository.upsertExtendingExpiration(
                identifier.type().name(),
                identifier.hash(),
                identifier.keyVersion(),
                now.plusDays(REJOIN_BLOCK_DAYS),
                requireSourceUserId(sourceUserId),
                now);
    }

    private RejoinBlockIdentifier requireIdentifier(RejoinBlockIdentifier identifier) {
        Objects.requireNonNull(identifier, "재가입 제한 식별자는 필수입니다.");
        if (identifier.type() == null
                || identifier.hash() == null
                || identifier.hash().isBlank()
                || identifier.keyVersion() <= 0) {
            throw new IllegalArgumentException("유효하지 않은 재가입 제한 식별자입니다.");
        }
        return identifier;
    }

    private LocalDateTime requireNow(LocalDateTime now) {
        return Objects.requireNonNull(now, "재가입 제한 기준 시각은 필수입니다.");
    }

    private Long requireSourceUserId(Long sourceUserId) {
        return Objects.requireNonNull(sourceUserId, "재가입 제한 출처 사용자 ID는 필수입니다.");
    }
}
