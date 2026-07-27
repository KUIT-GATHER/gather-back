package com.gather.gather.domain.user.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.user.service.ProfileImageCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileImageCleanupSchedulerTest {

    @Mock private ProfileImageCleanupService profileImageCleanupService;

    @Test
    @DisplayName("만료 업로드 정리가 실패해도 기존 이미지 삭제 재시도는 실행한다")
    void cleanupProfileImages_retriesPreviousDeletionWhenExpiredCleanupFails() {
        doThrow(new IllegalStateException("expired cleanup failed"))
                .when(profileImageCleanupService)
                .cleanupExpiredUploads();
        ProfileImageCleanupScheduler scheduler =
                new ProfileImageCleanupScheduler(profileImageCleanupService);

        scheduler.cleanupProfileImages();

        verify(profileImageCleanupService).retryPreviousObjectDeletions();
    }

    @Test
    @DisplayName("기존 이미지 삭제 재시도가 실패해도 만료 업로드 정리 결과는 유지한다")
    void cleanupProfileImages_runsExpiredCleanupWhenPreviousDeletionFails() {
        doThrow(new IllegalStateException("previous deletion failed"))
                .when(profileImageCleanupService)
                .retryPreviousObjectDeletions();
        ProfileImageCleanupScheduler scheduler =
                new ProfileImageCleanupScheduler(profileImageCleanupService);

        scheduler.cleanupProfileImages();

        verify(profileImageCleanupService).cleanupExpiredUploads();
    }
}
