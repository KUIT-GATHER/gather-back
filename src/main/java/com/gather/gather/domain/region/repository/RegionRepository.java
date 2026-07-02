package com.gather.gather.domain.region.repository;

import com.gather.gather.domain.region.entity.Region;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByCode(String code);

    /** parent를 fetch join하여 목록 조회 시 N+1을 피한다. */
    @Query("select r from Region r left join fetch r.parent")
    List<Region> findAllWithParent();
}
