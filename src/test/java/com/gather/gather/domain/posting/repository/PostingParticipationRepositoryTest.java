package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code uq_posting_participation_user_posting} DB 유니크 제약이 실제로 (user_id, posting_id) 중복 저장을 막는지
 * 검증한다. {@link com.gather.gather.domain.posting.service.PostingParticipationService}는 이 제약을 동시 요청
 * 방어 최후 수단으로 의존하므로, 목(mock) 리포지토리가 아닌 실제 DB 레벨에서 확인이 필요하다. {@code posting_participation}은 {@code
 * user_id}에 실제 FK(fk_posting_participation_user, V23)가 걸려 있어 존재하지 않는 임의의 user_id로는 저장 자체가 FK 위반으로
 * 실패한다 — 반드시 {@link UserRepository}로 저장한 실제 User의 id를 사용해야 한다.
 */
@SpringBootTest
@Transactional
class PostingParticipationRepositoryTest {

    @Autowired private PostingParticipationRepository postingParticipationRepository;

    @Autowired private PostingRepository postingRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private RegionRepository regionRepository;

    @Test
    void existsByUserIdAndPostingId_returnsTrue_whenParticipationExists() {
        Posting posting = postingRepository.save(posting());
        Long userId = applicant().getId();
        postingParticipationRepository.save(PostingParticipation.create(userId, posting.getId()));

        assertThat(
                        postingParticipationRepository.existsByUserIdAndPostingId(
                                userId, posting.getId()))
                .isTrue();
    }

    @Test
    void existsByUserIdAndPostingId_returnsFalse_whenParticipationDoesNotExist() {
        Posting posting = postingRepository.save(posting());
        Long userId = applicant().getId();

        assertThat(
                        postingParticipationRepository.existsByUserIdAndPostingId(
                                userId, posting.getId()))
                .isFalse();
    }

    @Test
    void save_throwsDataIntegrityViolationException_whenUserAndPostingAlreadyParticipating() {
        Posting posting = postingRepository.save(posting());
        Long userId = applicant().getId();
        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(userId, posting.getId()));

        assertThatThrownBy(
                        () ->
                                postingParticipationRepository.saveAndFlush(
                                        PostingParticipation.create(userId, posting.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_allowsSameUserToApplyToDifferentPostings() {
        Posting first = postingRepository.save(posting());
        Posting second = postingRepository.save(posting());
        Long userId = applicant().getId();

        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(userId, first.getId()));
        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(userId, second.getId()));

        assertThat(postingParticipationRepository.existsByUserIdAndPostingId(userId, first.getId()))
                .isTrue();
        assertThat(
                        postingParticipationRepository.existsByUserIdAndPostingId(
                                userId, second.getId()))
                .isTrue();
    }

    @Test
    void save_allowsDifferentUsersToApplyToSamePosting() {
        Posting posting = postingRepository.save(posting());
        Long firstUserId = applicant().getId();
        Long secondUserId = applicant().getId();

        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(firstUserId, posting.getId()));
        postingParticipationRepository.saveAndFlush(
                PostingParticipation.create(secondUserId, posting.getId()));

        assertThat(
                        postingParticipationRepository.existsByUserIdAndPostingId(
                                firstUserId, posting.getId()))
                .isTrue();
        assertThat(
                        postingParticipationRepository.existsByUserIdAndPostingId(
                                secondUserId, posting.getId()))
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

    private User applicant() {
        Region region =
                regionRepository.save(
                        Region.create("테스트구", 2, "999" + (System.nanoTime() % 10000000L), null));
        return userRepository.save(
                User.create(
                        "신청자",
                        LocalDate.of(1995, 1, 1),
                        Gender.MALE,
                        "010" + System.nanoTime() % 100000000L,
                        null,
                        null,
                        "app" + (System.nanoTime() % 1_000_000_000L),
                        null,
                        true,
                        true,
                        false,
                        region,
                        List.of()));
    }
}
