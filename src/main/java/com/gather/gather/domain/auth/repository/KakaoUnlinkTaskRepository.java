package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoUnlinkTaskRepository extends JpaRepository<KakaoUnlinkTask, Long> {

    Optional<KakaoUnlinkTask> findBySocialAccountIdAndGeneration(
            Long socialAccountId, long generation);

    @Query(
            value =
                    """
                    SELECT *
                    FROM kakao_unlink_task
                    WHERE status = 'PENDING'
                      AND next_attempt_at <= :databaseNow
                    ORDER BY next_attempt_at, id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<KakaoUnlinkTask> findDuePendingForUpdate(
            @Param("databaseNow") LocalDateTime databaseNow, @Param("limit") int limit);

    @Query(
            value =
                    """
                    SELECT *
                    FROM kakao_unlink_task
                    WHERE status = 'PROCESSING'
                      AND lease_expires_at <= :databaseNow
                    ORDER BY lease_expires_at, id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<KakaoUnlinkTask> findExpiredProcessingForUpdate(
            @Param("databaseNow") LocalDateTime databaseNow, @Param("limit") int limit);

    @Query(value = "SELECT UTC_TIMESTAMP(6)", nativeQuery = true)
    LocalDateTime currentUtcDateTime();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT task FROM KakaoUnlinkTask task WHERE task.id = :id")
    Optional<KakaoUnlinkTask> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT task
            FROM KakaoUnlinkTask task
            WHERE task.status = com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus.DEAD
              AND task.lastErrorType = com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType.CONFIGURATION
            ORDER BY task.id
            """)
    List<KakaoUnlinkTask> findAllConfigurationDeadForUpdate();
}
