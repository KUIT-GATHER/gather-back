package com.gather.gather.domain.region.repository;

import com.gather.gather.domain.region.entity.Region;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByCode(String code);

    /** parent와 regionGroup을 fetch join하여 목록 조회 시 N+1을 피한다. */
    @Query("select r from Region r left join fetch r.parent left join fetch r.regionGroup")
    List<Region> findAllWithParent();

    /**
     * regionId 자신과 그 자식(1단계) + 손자(2단계)의 id를 모두 반환한다. 시도(level 1)로 조회하면 시군구(1단계)뿐 아니라 그 소속
     * 읍/면/동(2단계)까지 포함되고, 시군구로 조회하면 읍/면/동(1단계)까지 포함된다.
     *
     * <p>parent를 반드시 명시적 left join으로 조인해야 한다 — r.parent.parent.id처럼 체이닝된 암묵적 경로 탐색은 JPQL에서 기본적으로
     * inner join으로 컴파일되므로, 조상 체인 중간에 parent가 null인 행(예: 최상위 시도, 부모가 없는 시군구)이 있으면 그 행 전체가 WHERE 절 평가
     * 전에 FROM 절에서 빠진다. p.parent.id는 p가 이미 left join으로 확보된 뒤 p 자신의 parent_id 컬럼을 읽는 것뿐이라 추가 조인 없이
     * 안전하다.
     */
    @Query(
            "select r.id from Region r left join r.parent p "
                    + "where r.id = :regionId or p.id = :regionId or p.parent.id = :regionId")
    List<Long> findIdsIncludingChildren(@Param("regionId") Long regionId);

    /**
     * 권역(9버튼)에 속한 시도들의 id와 그 자식(시군구) + 손자(읍/면/동) id를 모두 반환한다. 경상/전라/충청처럼 시도가 여러 개인 권역도 이 한 번의 조회로
     * 해결된다(regionGroup은 시도 행에만 설정되므로 r.regionGroup.id로 시도 자신을, p.regionGroup.id로 그 자식 시군구를,
     * gp.regionGroup.id로 그 손자 읍/면/동을 함께 잡는다).
     *
     * <p>parent 체인을 반드시 두 번의 명시적 left join으로 조인해야 한다. p.regionGroup.id는 p 자신의 region_group_id 컬럼을
     * 읽는 것뿐이라 추가 조인이 필요 없지만, gp.regionGroup.id는 p의 parent(조부모) 행 자체를 읽어야 하는 값이라 명시적으로 left join
     * p.parent gp를 선언해야 한다 — 암묵적 경로 탐색(p.parent.regionGroup.id)은 inner join으로 컴파일돼 조부모가 없는 행 전체를
     * FROM 절에서 걸러버린다.
     */
    @Query(
            "select r.id from Region r left join r.parent p left join p.parent gp "
                    + "where r.regionGroup.id = :groupId "
                    + "or p.regionGroup.id = :groupId "
                    + "or gp.regionGroup.id = :groupId")
    List<Long> findIdsIncludingChildrenByGroupId(@Param("groupId") Long groupId);
}
