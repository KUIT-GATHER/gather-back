package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
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

    @Test
    void findBookmarkedPostings_returnsOnlyThatUsersBookmarks_orderedByBookmarkedAtDesc() {
        Posting first = postingRepository.save(posting());
        Posting second = postingRepository.save(posting());
        Posting othersPosting = postingRepository.save(posting());
        Bookmark firstBookmark = Bookmark.create(1L, first.getId());
        Bookmark secondBookmark = Bookmark.create(1L, second.getId());
        // 두 저장 호출 사이 실제 경과 시간에 의존하면 클럭 해상도에 따라 흔들릴 수 있어, 북마크 시각을 명시적으로 벌려
        // "나중에 북마크한 것이 먼저 나온다"는 정렬 규칙만 결정적으로 검증한다.
        ReflectionTestUtils.setField(
                firstBookmark, "createdAt", LocalDateTime.of(2026, 7, 1, 0, 0));
        ReflectionTestUtils.setField(
                secondBookmark, "createdAt", LocalDateTime.of(2026, 7, 2, 0, 0));
        bookmarkRepository.saveAndFlush(firstBookmark);
        bookmarkRepository.saveAndFlush(secondBookmark);
        bookmarkRepository.saveAndFlush(Bookmark.create(2L, othersPosting.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(1L, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(Posting::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findBookmarkedPostings_filtersByCategory() {
        Posting environment =
                postingRepository.save(posting("테스트 공고", PostingCategory.ENVIRONMENT));
        Posting education = postingRepository.save(posting("테스트 공고", PostingCategory.EDUCATION));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, environment.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, education.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(
                        1L, PostingCategory.EDUCATION, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Posting::getId).containsExactly(education.getId());
    }

    @Test
    void findBookmarkedPostings_filtersByKeyword() {
        Posting matching =
                postingRepository.save(posting("동구 환경정화 봉사", PostingCategory.ENVIRONMENT));
        Posting nonMatching =
                postingRepository.save(posting("무관한 제목", PostingCategory.ENVIRONMENT));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, matching.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, nonMatching.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(1L, null, "환경정화", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Posting::getId).containsExactly(matching.getId());
    }

    @Test
    void findBookmarkedPostings_returnsEmptyPage_whenUserHasNoBookmarks() {
        postingRepository.save(posting());

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(1L, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }

    private Posting posting() {
        return posting("테스트 공고", PostingCategory.ENVIRONMENT);
    }

    private Posting posting(String title, PostingCategory category) {
        return Posting.builder()
                .title(title)
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(category)
                .build();
    }
}
