package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import java.time.LocalDateTime;
import java.util.List;

record LockedSocialSignupSession(
        SocialSignupSession target,
        List<SocialSignupSession> lockedPendingSessions,
        SocialSignupIdentitySnapshot identity) {

    LockedSocialSignupSession {
        if (target == null || lockedPendingSessions == null || identity == null) {
            throw new IllegalArgumentException("잠긴 가입 세션 context 값은 필수입니다.");
        }
        lockedPendingSessions = List.copyOf(lockedPendingSessions);
        boolean containsTarget =
                lockedPendingSessions.stream().anyMatch(session -> sameSession(session, target));
        if (!containsTarget) {
            throw new IllegalArgumentException("잠긴 가입 세션 목록에 대상 세션이 없습니다.");
        }
    }

    void consumeAndCancelOthers(LocalDateTime now) {
        target.consume(now);
        lockedPendingSessions.stream()
                .filter(session -> !sameSession(session, target))
                .filter(session -> session.getStatus() == SocialSignupSessionStatus.PENDING)
                .forEach(session -> session.cancel(now));
    }

    private static boolean sameSession(SocialSignupSession first, SocialSignupSession second) {
        if (first.getId() != null && second.getId() != null) {
            return first.getId().equals(second.getId());
        }
        return first.getTokenHash().equals(second.getTokenHash());
    }
}
