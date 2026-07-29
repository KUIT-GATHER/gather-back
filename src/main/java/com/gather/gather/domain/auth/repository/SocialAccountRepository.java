package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider, String providerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select sa
              from SocialAccount sa
              join fetch sa.user
             where sa.provider = :provider
               and sa.providerUserId = :providerUserId
            """)
    Optional<SocialAccount> findByProviderAndProviderUserIdForUpdate(
            SocialProvider provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    Optional<SocialAccount> findByUserIdAndProvider(Long userId, SocialProvider provider);

    /** 탈퇴했는데 연동 정보가 남은 계정 = 카카오 연결 해제 재처리 대기열. 별도 상태 컬럼 없이 row의 존재 자체가 큐 역할을 한다. */
    @Query(
            """
            select sa
              from SocialAccount sa
              join fetch sa.user u
             where u.status = :status
               and sa.id > :lastSeenId
             order by sa.id asc
            """)
    List<SocialAccount> findByUserStatusAfterId(
            UserStatus status, Long lastSeenId, Pageable pageable);
}
