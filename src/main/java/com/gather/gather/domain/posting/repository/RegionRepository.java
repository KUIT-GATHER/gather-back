package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Region;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByCode(String code);
}
