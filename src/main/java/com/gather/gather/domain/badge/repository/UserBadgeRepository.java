package com.gather.gather.domain.badge.repository;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    boolean existsByUserIdAndBadgeType(Long userId, BadgeType badgeType);

    List<UserBadge> findAllByUserIdOrderByEarnedAtDesc(Long userId);
}
