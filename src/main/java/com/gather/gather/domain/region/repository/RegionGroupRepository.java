package com.gather.gather.domain.region.repository;

import com.gather.gather.domain.region.entity.RegionGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionGroupRepository extends JpaRepository<RegionGroup, Long> {

    List<RegionGroup> findAllByOrderBySortOrderAsc();
}
