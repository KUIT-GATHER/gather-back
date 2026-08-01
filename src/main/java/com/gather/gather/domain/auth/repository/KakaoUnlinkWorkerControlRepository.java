package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface KakaoUnlinkWorkerControlRepository
        extends JpaRepository<KakaoUnlinkWorkerControl, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT control FROM KakaoUnlinkWorkerControl control WHERE control.id = 1")
    Optional<KakaoUnlinkWorkerControl> findSingletonForUpdate();
}
