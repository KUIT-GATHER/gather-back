package com.gather.gather.domain.region.repository;

import com.gather.gather.domain.region.entity.Region;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByCode(String code);

    /** parent를 fetch join하여 목록 조회 시 N+1을 피한다. */
    @Query("select r from Region r left join fetch r.parent")
    List<Region> findAllWithParent();

    /**
     * regionId 자신과 그 직계 자식(구/군)의 id를 모두 반환한다. 현재 시드 데이터는 시도(level 1)-시군구(level 3) 2단 계층만 존재해 직계 자식
     * 조회만으로 충분하다(더 깊은 계층은 데이터 자체가 없음).
     */
    @Query("select r.id from Region r where r.id = :regionId or r.parent.id = :regionId")
    List<Long> findIdsIncludingChildren(@Param("regionId") Long regionId);
}
