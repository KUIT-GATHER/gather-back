package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.EmailVerification;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmail(String email);

    Optional<EmailVerification> findByVerificationId(String verificationId);

    boolean existsByEmail(String email);

    @Modifying(flushAutomatically = true)
    @Query("delete from EmailVerification e where e.email = :email")
    int deleteAllByEmail(String email);

    // 동시 재발송·인증 시도가 검사와 카운터 갱신 사이에 끼어들지 못하도록 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailVerification e where e.email = :email")
    Optional<EmailVerification> findByEmailForUpdate(String email);

    // 최초 발송 실패 시 이후 상태 변경이 없는 해당 발송 세대만 삭제한다.
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("delete from EmailVerification e where e.id = :id and e.version = :version")
    int deleteByIdAndVersion(Long id, Long version);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(
            """
            update EmailVerification e
               set e.verificationId = :verificationId,
                   e.code = :code,
                   e.verified = :verified,
                   e.expiresAt = :expiresAt,
                   e.verifiedAt = :verifiedAt,
                   e.consumedAt = :consumedAt,
                   e.createdAt = :createdAt,
                   e.dailySendCount = :dailySendCount,
                   e.attemptCount = :attemptCount,
                   e.version = e.version + 1
             where e.id = :id
               and e.version = :failedVersion
            """)
    int restoreAfterFailedResend(
            Long id,
            Long failedVersion,
            String verificationId,
            String code,
            boolean verified,
            LocalDateTime expiresAt,
            LocalDateTime verifiedAt,
            LocalDateTime consumedAt,
            LocalDateTime createdAt,
            int dailySendCount,
            int attemptCount);
}
