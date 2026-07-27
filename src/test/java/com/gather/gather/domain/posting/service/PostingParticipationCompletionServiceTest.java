package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostingParticipationCompletionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PostingParticipationCompletionService completionService;

    @BeforeEach
    void setUp() {
        completionService =
                new PostingParticipationCompletionService(
                        postingParticipationRepository, postingRepository, eventPublisher);
    }

    @Test
    @DisplayName(
            "completes a participation whose posting activity end date has passed and publishes an event")
    void completeExpiredParticipations_completesAndPublishesEvent_whenActEndDatePassed() {
        PostingParticipation participation = PostingParticipation.create(USER_ID, 10L);
        Posting expiredPosting = posting(10L, LocalDate.now().minusDays(1), null);

        when(postingParticipationRepository.findByStatusIn(anyCollection()))
                .thenReturn(List.of(participation));
        when(postingRepository.findAllById(List.of(10L))).thenReturn(List.of(expiredPosting));

        int count = completionService.completeExpiredParticipations();

        assertThat(count).isEqualTo(1);
        assertThat(participation.getStatus()).isEqualTo(PostingParticipationStatus.COMPLETED);
        verify(eventPublisher).publishEvent(new PostingParticipationCompletedEvent(USER_ID, 10L));
    }

    @Test
    @DisplayName(
            "does not complete a participation whose posting activity is still today or in the future")
    void completeExpiredParticipations_skips_whenActEndDateNotPassed() {
        PostingParticipation participation = PostingParticipation.create(USER_ID, 20L);
        Posting ongoingPosting = posting(20L, LocalDate.now(), null);

        when(postingParticipationRepository.findByStatusIn(anyCollection()))
                .thenReturn(List.of(participation));
        when(postingRepository.findAllById(List.of(20L))).thenReturn(List.of(ongoingPosting));

        int count = completionService.completeExpiredParticipations();

        assertThat(count).isZero();
        assertThat(participation.getStatus()).isEqualTo(PostingParticipationStatus.APPLIED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("falls back to actStartDate when actEndDate is null")
    void completeExpiredParticipations_fallsBackToActStartDate_whenActEndDateNull() {
        PostingParticipation participation = PostingParticipation.create(USER_ID, 30L);
        Posting singleDayPosting = posting(30L, null, LocalDate.now().minusDays(2));

        when(postingParticipationRepository.findByStatusIn(anyCollection()))
                .thenReturn(List.of(participation));
        when(postingRepository.findAllById(List.of(30L))).thenReturn(List.of(singleDayPosting));

        int count = completionService.completeExpiredParticipations();

        assertThat(count).isEqualTo(1);
        assertThat(participation.getStatus()).isEqualTo(PostingParticipationStatus.COMPLETED);
    }

    @Test
    @DisplayName(
            "returns 0 without querying postings when there are no APPLIED/CONFIRMED participations")
    void completeExpiredParticipations_returnsZero_whenNoCandidates() {
        when(postingParticipationRepository.findByStatusIn(anyCollection())).thenReturn(List.of());

        int count = completionService.completeExpiredParticipations();

        assertThat(count).isZero();
        verify(postingRepository, never()).findAllById(any());
    }

    /** actEndDate가 우선이고, 없으면 actStartDate를 활동종료 기준으로 쓴다(순서: actEndDate, actStartDate). */
    private Posting posting(Long id, LocalDate actEndDate, LocalDate actStartDateFallback) {
        Posting createdPosting =
                Posting.builder()
                        .title("테스트 공고 " + id)
                        .status(PostingStatus.RECRUITING)
                        .activityDate(actEndDate != null ? actEndDate : actStartDateFallback)
                        .actStartDate(actEndDate != null ? actEndDate : actStartDateFallback)
                        .actEndDate(actEndDate)
                        .category(PostingCategory.ENVIRONMENT)
                        .build();
        ReflectionTestUtils.setField(createdPosting, "id", id);
        return createdPosting;
    }
}
