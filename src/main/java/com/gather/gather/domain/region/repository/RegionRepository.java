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
     * regionId 자신과 그 하위 지역 id를 모두 반환한다. 시도(level 1) → 시군구(level 2) → 읍/면/동(level 4) 3단계 구조이므로(level
     * 3은 사용하지 않음), 2-hop(자식+손자)까지 조회하면 시도로 조회해도 그 소속 읍/면/동까지 모두 포함된다.
     */
    @Query(
            "select r.id from Region r "
                    + "left join r.parent p "
                    + "left join p.parent gp "
                    + "where r.id = :regionId or p.id = :regionId or gp.id = :regionId")
    List<Long> findIdsIncludingChildren(@Param("regionId") Long regionId);

    /** 특정 지역의 직계 자식 목록(예: 시군구의 읍/면/동)을 반환한다. */
    @Query("select r from Region r where r.parent.id = :parentId")
    List<Region> findByParentId(@Param("parentId") Long parentId);
}
