package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRejoinBlockRepository extends JpaRepository<AccountRejoinBlock, Long> {

    Optional<AccountRejoinBlock> findByIdentifierTypeAndIdentifierHash(
            AccountRejoinBlockIdentifierType identifierType, String identifierHash);

    boolean existsByIdentifierTypeAndIdentifierHashAndExpiresAtAfter(
            AccountRejoinBlockIdentifierType identifierType,
            String identifierHash,
            LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    insert into account_rejoin_block (
                        identifier_type,
                        identifier_hash,
                        key_version,
                        expires_at,
                        source_user_id,
                        created_at
                    )
                    values (
                        :identifierType,
                        :identifierHash,
                        :keyVersion,
                        :expiresAt,
                        :sourceUserId,
                        :createdAt
                    )
                    on duplicate key update
                        expires_at = greatest(expires_at, :expiresAt)
                    """,
            nativeQuery = true)
    int upsertExtendingExpiration(
            @Param("identifierType") String identifierType,
            @Param("identifierHash") String identifierHash,
            @Param("keyVersion") int keyVersion,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("sourceUserId") Long sourceUserId,
            @Param("createdAt") LocalDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select b
            from AccountRejoinBlock b
            where b.identifierType = :identifierType
              and b.identifierHash = :identifierHash
            """)
    Optional<AccountRejoinBlock> findByIdentifierForUpdate(
            @Param("identifierType") AccountRejoinBlockIdentifierType identifierType,
            @Param("identifierHash") String identifierHash);
}
