package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndProviderUserKey(
            SocialProvider provider, String providerUserKey);

    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndLegacyProviderUserId(
            SocialProvider provider, String legacyProviderUserId);
}
