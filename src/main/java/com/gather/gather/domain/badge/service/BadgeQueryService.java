package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.dto.UserBadgeResponse;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.global.util.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeQueryService {

    private final UserBadgeRepository userBadgeRepository;

    public List<UserBadgeResponse> getMyBadges() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userBadgeRepository.findAllByUserIdOrderByEarnedAtDesc(userId).stream()
                .map(UserBadgeResponse::from)
                .toList();
    }
}
