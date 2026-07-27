package com.gather.gather.domain.badge.repository;

import com.gather.gather.domain.badge.entity.UserBadge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);

    List<UserBadge> findByUserId(Long userId);

    long countByUserId(Long userId);
}
