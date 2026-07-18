package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.posting.repository.PostingSearchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostingSearchLogServiceTest {

    @Mock private PostingSearchLogRepository postingSearchLogRepository;

    private PostingSearchLogService postingSearchLogService;

    @BeforeEach
    void setUp() {
        postingSearchLogService = new PostingSearchLogService(postingSearchLogRepository);
    }

    @Test
    @DisplayName("log saves a search log entry for the keyword")
    void log_savesSearchLog() {
        postingSearchLogService.log("유기견봉사");

        verify(postingSearchLogRepository).save(any());
    }

    @Test
    @DisplayName("log swallows repository failures so the caller is never affected")
    void log_swallowsRepositoryFailure() {
        doThrow(new RuntimeException("db down")).when(postingSearchLogRepository).save(any());

        assertThatCode(() -> postingSearchLogService.log("유기견봉사")).doesNotThrowAnyException();
    }
}
