package com.gather.gather.domain.posting.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.posting.service.PostingLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostingCleanupSchedulerTest {

    @Mock private PostingLifecycleService postingLifecycleService;

    @Test
    @DisplayName("cleanupPostings이 실패해도 예외를 전파하지 않는다")
    void cleanupPostings_doesNotPropagateException_whenServiceFails() {
        doThrow(new IllegalStateException("deactivate failed"))
                .when(postingLifecycleService)
                .deactivateExpiredPostings();
        PostingCleanupScheduler scheduler = new PostingCleanupScheduler(postingLifecycleService);

        scheduler.cleanupPostings();

        verify(postingLifecycleService).deactivateExpiredPostings();
    }

    @Test
    @DisplayName("clearExpiredPostingContent이 실패해도 예외를 전파하지 않는다")
    void clearExpiredPostingContent_doesNotPropagateException_whenServiceFails() {
        doThrow(new IllegalStateException("clear content failed"))
                .when(postingLifecycleService)
                .clearExpiredPostingContent();
        PostingCleanupScheduler scheduler = new PostingCleanupScheduler(postingLifecycleService);

        scheduler.clearExpiredPostingContent();

        verify(postingLifecycleService).clearExpiredPostingContent();
    }
}
