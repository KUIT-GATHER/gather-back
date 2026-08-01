package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialSignupSessionRepository extends JpaRepository<SocialSignupSession, Long> {

    Optional<SocialSignupSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT session
            FROM SocialSignupSession session
            WHERE session.provider = :provider
              AND session.providerUserKey = :providerUserKey
              AND session.status = :status
            ORDER BY session.id
            """)
    List<SocialSignupSession> findAllByIdentityAndStatusForUpdate(
            @Param("provider") SocialProvider provider,
            @Param("providerUserKey") String providerUserKey,
            @Param("status") SocialSignupSessionStatus status);
}
