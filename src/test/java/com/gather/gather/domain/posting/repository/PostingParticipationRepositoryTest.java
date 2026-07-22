package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code uq_posting_participation_user_posting} DB 유니크 제약이 실제로 (user_id, posting_id) 중복 저장을 막는지
 * 검증한다. {@link com.gather.gather.domain.posting.service.PostingParticipationService}는 이 제약을 동시 요청
 * 방어 최후 수단으로 의존하므로, 목(mock) 리포지토리가 아닌 실제 DB 레벨에서 확인이 필요하다.
 */
@SpringBootTest
@Transactional
class PostingParticipationRepositoryTest {

    @Autowired private PostingParticipationRepository postingParticipationRepository;

    @Autowired private PostingRepository postingRepository;

    @Test
    void existsByUserIdAndPostingId_returnsTrue_whenParticipationExists() {
        Posting posting = postingRepository.save(posting());
        postingParticipationRepository.save(PostingParticipation.create(1L, posting.getId()));

        assertThat(postingParticipationRepository.existsByUserIdAndPostingId(1L, posting.getId()))
                .isTrue();
    }

    @Test
    void existsByUserIdAndPostingId_returnsFalse_whenParticipationDoesNotExist() {
        Posting posting = postingRepository.save(posting());

        assertThat(postingParticipationRepository.existsByUserIdAndPostingId(1L, posting.getId()))
                .isFalse();
    }

    @Test
    void save_throwsDataIntegrityViolationException_whenUserAndPostingAlreadyParticipating() {
        Posting posting = postingRepository.save(posting());
        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(1L, posting.getId()));

        assertThatThrownBy(
                        () ->
                                postingParticipationRepository.saveAndFlush(
                                        PostingParticipation.create(1L, posting.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_allowsSameUserToApplyToDifferentPostings() {
        Posting first = postingRepository.save(posting());
        Posting second = postingRepository.save(posting());

        postingParticipationRepository.saveAndFlush(PostingParticipation.create(1L, first.getId()));
        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(1L, second.getId()));

        assertThat(postingParticipationRepository.existsByUserIdAndPostingId(1L, first.getId()))
                .isTrue();
        assertThat(postingParticipationRepository.existsByUserIdAndPostingId(1L, second.getId()))
                .isTrue();
    }

    @Test
    void save_allowsDifferentUsersToApplyToSamePosting() {
        Posting posting = postingRepository.save(posting());

        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(1L, posting.getId()));
        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(2L, posting.getId()));

        assertThat(postingParticipationRepository.existsByUserIdAndPostingId(1L, posting.getId()))
                .isTrue();
        assertThat(postingParticipationRepository.existsByUserIdAndPostingId(2L, posting.getId()))
                .isTrue();
    }

    private Posting posting() {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(PostingCategory.ENVIRONMENT)
                .build();
    }
}
