package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.KakaoUnlinkMonitorControl;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoUnlinkMonitorControlRepository
        extends JpaRepository<KakaoUnlinkMonitorControl, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select control from KakaoUnlinkMonitorControl control where control.id = :id")
    Optional<KakaoUnlinkMonitorControl> findByIdForUpdate(@Param("id") Long id);

    default Optional<KakaoUnlinkMonitorControl> findSingletonForUpdate() {
        return findByIdForUpdate(KakaoUnlinkMonitorControl.SINGLETON_ID);
    }

    @Query(value = "select UTC_TIMESTAMP(6)", nativeQuery = true)
    Instant currentUtcInstant();

    default LocalDateTime currentUtcDateTime() {
        return LocalDateTime.ofInstant(currentUtcInstant(), ZoneOffset.UTC);
    }
}
