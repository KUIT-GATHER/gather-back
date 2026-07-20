package com.gather.gather.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileImageDeletionListener {

    private final ProfileImageCleanupService profileImageCleanupService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deletePreviousImage(ProfileImageReplacedEvent event) {
        try {
            profileImageCleanupService.deletePreviousObject(event.uploadId());
        } catch (RuntimeException exception) {
            log.warn(
                    "기존 프로필 이미지 객체 삭제에 실패했습니다. 재시도 대상으로 유지합니다: uploadId={}, cause={}",
                    event.uploadId(),
                    exception.getClass().getSimpleName());
        }
    }
}
