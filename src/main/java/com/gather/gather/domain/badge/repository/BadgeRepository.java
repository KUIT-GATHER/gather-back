package com.gather.gather.domain.badge.repository;

import com.gather.gather.domain.badge.entity.Badge;
import com.gather.gather.domain.badge.entity.BadgeCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    Optional<Badge> findByCode(BadgeCode code);

    List<Badge> findAllByOrderByDisplayOrderAsc();
}
