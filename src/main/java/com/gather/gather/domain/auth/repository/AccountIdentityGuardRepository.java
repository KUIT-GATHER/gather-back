package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.AccountIdentityGuard;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountIdentityGuardRepository extends JpaRepository<AccountIdentityGuard, Long> {

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    insert into account_identity_guard (
                        identity_type,
                        identity_hash,
                        key_version,
                        created_at
                    )
                    values (
                        :identityType,
                        :identityHash,
                        :keyVersion,
                        :createdAt
                    )
                    on duplicate key update id = id
                    """,
            nativeQuery = true)
    int upsert(
            @Param("identityType") String identityType,
            @Param("identityHash") String identityHash,
            @Param("keyVersion") int keyVersion,
            @Param("createdAt") LocalDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select guard
            from AccountIdentityGuard guard
            where guard.identityType = :identityType
              and guard.identityHash = :identityHash
              and guard.keyVersion = :keyVersion
            """)
    Optional<AccountIdentityGuard> findByIdentityForUpdate(
            @Param("identityType") AccountRejoinBlockIdentifierType identityType,
            @Param("identityHash") String identityHash,
            @Param("keyVersion") int keyVersion);
}
