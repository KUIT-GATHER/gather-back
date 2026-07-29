package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRejoinBlockRepository extends JpaRepository<AccountRejoinBlock, Long> {

    Optional<AccountRejoinBlock> findByIdentifierTypeAndIdentifierHash(
            AccountRejoinBlockIdentifierType identifierType, String identifierHash);

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
