package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select token.user.id from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken token where token.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
