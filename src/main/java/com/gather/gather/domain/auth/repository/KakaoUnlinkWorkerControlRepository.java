package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoUnlinkWorkerControlRepository
        extends JpaRepository<KakaoUnlinkWorkerControl, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT control FROM KakaoUnlinkWorkerControl control WHERE control.id = :singletonId")
    Optional<KakaoUnlinkWorkerControl> findByIdForUpdate(@Param("singletonId") Long singletonId);

    default Optional<KakaoUnlinkWorkerControl> findSingletonForUpdate() {
        return findByIdForUpdate(KakaoUnlinkWorkerControl.SINGLETON_ID);
    }
}
