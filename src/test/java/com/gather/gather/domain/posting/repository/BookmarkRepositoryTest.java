package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code uk_bookmark_user_posting} DB 유니크 제약이 실제로 (user_id, posting_id) 중복 저장을 막는지 검증한다. {@link
 * com.gather.gather.domain.posting.service.BookmarkService}는 이 제약을 동시 요청 방어 최후 수단으로 의존하므로, 목(mock)
 * 리포지토리가 아닌 실제 DB 레벨에서 확인이 필요하다.
 */
@SpringBootTest
@Transactional
class BookmarkRepositoryTest {

    @Autowired private BookmarkRepository bookmarkRepository;

    @Autowired private PostingRepository postingRepository;

    @Test
    void existsByUserIdAndPostingId_returnsTrue_whenBookmarkExists() {
        Posting posting = postingRepository.save(posting());
        bookmarkRepository.save(Bookmark.create(1L, posting.getId()));

        assertThat(bookmarkRepository.existsByUserIdAndPostingId(1L, posting.getId())).isTrue();
    }

    @Test
    void existsByUserIdAndPostingId_returnsFalse_whenBookmarkDoesNotExist() {
        Posting posting = postingRepository.save(posting());

        assertThat(bookmarkRepository.existsByUserIdAndPostingId(1L, posting.getId())).isFalse();
    }

    @Test
    void findByUserIdAndPostingId_returnsBookmark_whenExists() {
        Posting posting = postingRepository.save(posting());
        Bookmark saved = bookmarkRepository.save(Bookmark.create(1L, posting.getId()));

        assertThat(bookmarkRepository.findByUserIdAndPostingId(1L, posting.getId()))
                .contains(saved);
    }

    @Test
    void findByUserIdAndPostingId_returnsEmpty_whenNotExists() {
        Posting posting = postingRepository.save(posting());

        assertThat(bookmarkRepository.findByUserIdAndPostingId(1L, posting.getId())).isEmpty();
    }

    @Test
    void save_throwsDataIntegrityViolationException_whenUserAndPostingAlreadyBookmarked() {
        Posting posting = postingRepository.save(posting());
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, posting.getId()));

        assertThatThrownBy(
                        () -> bookmarkRepository.saveAndFlush(Bookmark.create(1L, posting.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_allowsSameUserToBookmarkDifferentPostings() {
        Posting first = postingRepository.save(posting());
        Posting second = postingRepository.save(posting());

        bookmarkRepository.saveAndFlush(Bookmark.create(1L, first.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, second.getId()));

        assertThat(bookmarkRepository.existsByUserIdAndPostingId(1L, first.getId())).isTrue();
        assertThat(bookmarkRepository.existsByUserIdAndPostingId(1L, second.getId())).isTrue();
    }

    @Test
    void save_allowsDifferentUsersToBookmarkSamePosting() {
        Posting posting = postingRepository.save(posting());

        bookmarkRepository.saveAndFlush(Bookmark.create(1L, posting.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(2L, posting.getId()));

        assertThat(bookmarkRepository.existsByUserIdAndPostingId(1L, posting.getId())).isTrue();
        assertThat(bookmarkRepository.existsByUserIdAndPostingId(2L, posting.getId())).isTrue();
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
