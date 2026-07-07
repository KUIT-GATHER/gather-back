package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PostingRepository#search}의 실제 DB 동작 검증. 특히 regionIds가 null이 아니라 빈 리스트로 들어왔을 때(존재하지 않는
 * regionId 필터) 예외 없이 빈 결과를 반환하는지가 핵심 검증 대상이다.
 */
@SpringBootTest
@Transactional
class PostingRepositoryTest {

    @Autowired private PostingRepository postingRepository;

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Test
    void search_filtersByStatus_whenRegionAndDateFiltersAreNull() {
        postingRepository.save(posting(PostingStatus.RECRUITING, 1L, LocalDate.of(2026, 7, 1)));
        postingRepository.save(posting(PostingStatus.CLOSED, 1L, LocalDate.of(2026, 7, 1)));

        var result = postingRepository.search(PostingStatus.RECRUITING, null, null, null, PAGEABLE);

        assertThat(result.getContent()).allMatch(p -> p.getStatus() == PostingStatus.RECRUITING);
    }

    @Test
    void search_returnsEmpty_withoutException_whenRegionIdsIsEmptyList() {
        postingRepository.save(posting(PostingStatus.RECRUITING, 1L, LocalDate.of(2026, 7, 1)));

        var result =
                postingRepository.search(PostingStatus.RECRUITING, List.of(), null, null, PAGEABLE);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_filtersByRegionIds_whenProvided() {
        Posting matching =
                postingRepository.save(
                        posting(PostingStatus.RECRUITING, 42L, LocalDate.of(2026, 7, 1)));
        postingRepository.save(posting(PostingStatus.RECRUITING, 43L, LocalDate.of(2026, 7, 1)));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, List.of(42L), null, null, PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(matching.getId());
    }

    @Test
    void search_filtersByNoticeDateRange_whenProvided() {
        Posting inRange =
                save(
                        PostingStatus.RECRUITING,
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 20));
        save(PostingStatus.RECRUITING, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING,
                        null,
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        PAGEABLE);

        assertThat(result.getContent()).extracting(Posting::getId).containsExactly(inRange.getId());
    }

    private Posting save(PostingStatus status, LocalDate noticeStart, LocalDate noticeEnd) {
        return postingRepository.save(
                Posting.builder()
                        .title("테스트 공고")
                        .status(status)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .noticeStartDate(noticeStart)
                        .noticeEndDate(noticeEnd)
                        .categoryId(1L)
                        .build());
    }

    private Posting posting(PostingStatus status, Long regionId, LocalDate activityDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(status)
                .activityDate(activityDate)
                .regionId(regionId)
                .categoryId(1L)
                .build();
    }
}
