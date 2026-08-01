package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KakaoUnlinkTaskRepository extends JpaRepository<KakaoUnlinkTask, Long> {

    Optional<KakaoUnlinkTask> findBySocialAccountIdAndGeneration(
            Long socialAccountId, long generation);
}
