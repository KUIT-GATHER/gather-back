package com.gather.gather.domain.posting.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.service.PostingSyncResult;
import com.gather.gather.domain.posting.service.VmsPostingSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VmsPostingSyncSchedulerTest {

    @Mock private VmsPostingSyncService vmsPostingSyncService;

    @Test
    @DisplayName("syncPostings이 실패해도 예외를 전파하지 않는다")
    void syncPostings_doesNotPropagateException_whenServiceFails() {
        when(vmsPostingSyncService.syncRecentPostings())
                .thenThrow(new IllegalStateException("crawl failed"));
        VmsPostingSyncScheduler scheduler = new VmsPostingSyncScheduler(vmsPostingSyncService);

        scheduler.syncPostings();

        verify(vmsPostingSyncService).syncRecentPostings();
    }

    @Test
    @DisplayName("syncPostings이 정상 완료되면 결과를 로깅만 하고 별다른 동작을 하지 않는다")
    void syncPostings_logsResult_whenServiceSucceeds() {
        when(vmsPostingSyncService.syncRecentPostings())
                .thenReturn(new PostingSyncResult(1, 1, 0, 0, 0));
        VmsPostingSyncScheduler scheduler = new VmsPostingSyncScheduler(vmsPostingSyncService);

        scheduler.syncPostings();

        verify(vmsPostingSyncService).syncRecentPostings();
    }
}
