package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostingLifecycleServiceTest {

    @Mock private PostingRepository postingRepository;

    private PostingLifecycleService postingLifecycleService;

    @BeforeEach
    void setUp() {
        postingLifecycleService = new PostingLifecycleService(postingRepository);
    }

    @Test
    @DisplayName(
            "deactivateExpiredPostings calls repository with today's date and returns updated count")
    void deactivateExpiredPostings_callsRepositoryWithToday_returnsUpdatedCount() {
        when(postingRepository.deactivateExpired(any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(3);

        int result = postingLifecycleService.deactivateExpiredPostings();

        assertThat(result).isEqualTo(3);
        ArgumentCaptor<LocalDate> todayCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(postingRepository)
                .deactivateExpired(todayCaptor.capture(), any(LocalDateTime.class));
        assertThat(todayCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName(
            "clearExpiredPostingContent calls repository with a cutoff date one month ago and"
                    + " returns updated count")
    void clearExpiredPostingContent_callsRepositoryWithOneMonthAgo_returnsUpdatedCount() {
        when(postingRepository.clearExpiredContent(any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(5);

        int result = postingLifecycleService.clearExpiredPostingContent();

        assertThat(result).isEqualTo(5);
        ArgumentCaptor<LocalDate> cutoffCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(postingRepository)
                .clearExpiredContent(cutoffCaptor.capture(), any(LocalDateTime.class));
        assertThat(cutoffCaptor.getValue()).isEqualTo(LocalDate.now().minusMonths(1));
    }
}
