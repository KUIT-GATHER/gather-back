package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.SocialSignupSession;
import java.time.LocalDateTime;
import java.util.List;

public record LockedPendingSocialSignupSessions(List<SocialSignupSession> sessions) {

    public LockedPendingSocialSignupSessions {
        sessions = List.copyOf(sessions);
    }

    public void cancelAll(LocalDateTime now) {
        sessions.forEach(session -> session.cancel(now));
    }
}
