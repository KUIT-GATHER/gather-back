package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    Optional<SocialAccount> findByUserIdAndProvider(Long userId, SocialProvider provider);
}
