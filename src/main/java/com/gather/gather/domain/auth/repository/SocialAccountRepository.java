package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndProviderUserKey(
            SocialProvider provider, String providerUserKey);

    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndLegacyProviderUserId(
            SocialProvider provider, String legacyProviderUserId);

    boolean existsByUserIdAndProviderAndLinkStatus(
            Long userId, SocialProvider provider, SocialAccountLinkStatus linkStatus);

    @Query(
            """
            SELECT new com.gather.gather.domain.auth.repository.SocialAccountIdentitySnapshot(
                account.id,
                account.provider,
                account.providerUserKey,
                account.providerUserKeyVersion,
                account.linkStatus,
                account.generation
            )
            FROM SocialAccount account
            WHERE account.user.id = :userId
              AND account.provider = :provider
            ORDER BY account.id
            """)
    List<SocialAccountIdentitySnapshot> findIdentitySnapshotsByUserIdAndProvider(
            @Param("userId") Long userId, @Param("provider") SocialProvider provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM SocialAccount account WHERE account.id = :id")
    Optional<SocialAccount> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT account
            FROM SocialAccount account
            WHERE account.provider = :provider
              AND account.providerUserKey = :providerUserKey
            """)
    Optional<SocialAccount> findByProviderAndProviderUserKeyForUpdate(
            @Param("provider") SocialProvider provider,
            @Param("providerUserKey") String providerUserKey);
}
