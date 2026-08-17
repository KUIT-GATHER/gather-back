package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("select token.user.id from PasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    Optional<PasswordResetToken> findByUserId(Long userId);

    // 잠금 아래에서 다른 엔티티와 함께 호출되므로, 영속성 컨텍스트를 비우지 않고 flush만 맞춘다.
    @Modifying(flushAutomatically = true)
    @Query("delete from PasswordResetToken token where token.user.id = :userId")
    int deleteAllByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetToken token where token.expiresAt <= :cutoff")
    int deleteAllExpiredAtOrBefore(LocalDateTime cutoff);
}
