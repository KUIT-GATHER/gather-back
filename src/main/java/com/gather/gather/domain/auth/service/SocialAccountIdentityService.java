package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialAccountIdentityService {

    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountProviderIdCipher providerIdCipher;

    /** 단계적 migration 동안 새 HMAC 조회를 우선하고, 기존 row만 평문 컬럼으로 찾아 즉시 새 식별자 구조로 전환한다. */
    @Transactional
    public Optional<SocialAccount> findKakaoAccount(
            String providerUserId, RejoinBlockIdentifier identifier) {
        validateKakaoIdentifier(identifier);

        Optional<SocialAccount> current =
                socialAccountRepository.findByProviderAndProviderUserKey(
                        SocialProvider.KAKAO, identifier.hash());
        if (current.isPresent()) {
            validateStoredIdentifier(current.get(), identifier);
            return current;
        }

        Optional<SocialAccount> legacy =
                socialAccountRepository.findByProviderAndLegacyProviderUserId(
                        SocialProvider.KAKAO, providerUserId);
        legacy.ifPresent(
                account -> {
                    if (account.requiresLegacyIdentityBackfill()) {
                        EncryptedProviderUserId encryptedProviderUserId =
                                providerIdCipher.encrypt(providerUserId);
                        account.backfillLegacyIdentity(
                                identifier.hash(),
                                identifier.keyVersion(),
                                encryptedProviderUserId,
                                LocalDateTime.now());
                    } else {
                        validateStoredIdentifier(account, identifier);
                    }
                });
        return legacy;
    }

    @Transactional(readOnly = true)
    public Optional<SocialAccount> findByProviderAndKey(
            SocialProvider provider, RejoinBlockIdentifier identifier) {
        validateKakaoIdentifier(identifier);
        if (provider != SocialProvider.KAKAO) {
            throw new IllegalArgumentException("카카오 소셜 제공자가 필요합니다.");
        }
        Optional<SocialAccount> account =
                socialAccountRepository.findByProviderAndProviderUserKey(
                        provider, identifier.hash());
        account.ifPresent(found -> validateStoredIdentifier(found, identifier));
        return account;
    }

    private void validateKakaoIdentifier(RejoinBlockIdentifier identifier) {
        if (identifier == null || identifier.type() != AccountRejoinBlockIdentifierType.KAKAO) {
            throw new IllegalArgumentException("카카오 식별자 HMAC 결과가 필요합니다.");
        }
    }

    private void validateStoredIdentifier(
            SocialAccount socialAccount, RejoinBlockIdentifier identifier) {
        if (!socialAccount.matchesProviderUserKey(identifier.hash(), identifier.keyVersion())) {
            throw new IllegalStateException("저장된 소셜 계정 조회 키 버전이 현재 HMAC 결과와 일치하지 않습니다.");
        }
    }
}
