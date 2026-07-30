package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SocialSignupSessionPersistenceService {

    private final SocialSignupSessionRepository sessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNewAttempt(SocialSignupSession session) {
        sessionRepository.saveAndFlush(session);
    }
}
