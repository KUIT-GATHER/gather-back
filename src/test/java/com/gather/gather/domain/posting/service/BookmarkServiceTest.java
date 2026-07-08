package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.dto.BookmarkResponse;
import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POSTING_ID = 10L;

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private PostingRepository postingRepository;

    private BookmarkService bookmarkService;

    @BeforeEach
    void setUp() {
        bookmarkService = new BookmarkService(bookmarkRepository, postingRepository);
    }

    @Test
    @DisplayName(
            "addBookmark saves a bookmark when the posting exists and is not already bookmarked")
    void addBookmark_savesBookmark_whenPostingExistsAndNotDuplicate() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.existsById(POSTING_ID)).thenReturn(true);
            when(bookmarkRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);

            BookmarkResponse response = bookmarkService.addBookmark(POSTING_ID);

            assertThat(response.postingId()).isEqualTo(POSTING_ID);
            assertThat(response.bookmarked()).isTrue();
            verify(bookmarkRepository).saveAndFlush(any(Bookmark.class));
        }
    }

    @Test
    @DisplayName("addBookmark throws POSTING_NOT_FOUND when the posting does not exist")
    void addBookmark_throwsPostingNotFound_whenPostingMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.existsById(POSTING_ID)).thenReturn(false);

            assertThatThrownBy(() -> bookmarkService.addBookmark(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POSTING_NOT_FOUND);

            verify(bookmarkRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("addBookmark throws BOOKMARK_DUPLICATE when already bookmarked")
    void addBookmark_throwsBookmarkDuplicate_whenAlreadyBookmarked() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.existsById(POSTING_ID)).thenReturn(true);
            when(bookmarkRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> bookmarkService.addBookmark(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKMARK_DUPLICATE);

            verify(bookmarkRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName(
            "addBookmark throws BOOKMARK_DUPLICATE when a concurrent request wins the unique"
                    + " constraint race")
    void addBookmark_throwsBookmarkDuplicate_whenConcurrentInsertViolatesUniqueConstraint() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.existsById(POSTING_ID)).thenReturn(true);
            when(bookmarkRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);
            when(bookmarkRepository.saveAndFlush(any(Bookmark.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate entry"));

            assertThatThrownBy(() -> bookmarkService.addBookmark(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKMARK_DUPLICATE);
        }
    }

    @Test
    @DisplayName("removeBookmark deletes the bookmark when it exists")
    void removeBookmark_deletesBookmark_whenExists() {
        Bookmark bookmark = Bookmark.create(USER_ID, POSTING_ID);
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(bookmarkRepository.findByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(Optional.of(bookmark));

            BookmarkResponse response = bookmarkService.removeBookmark(POSTING_ID);

            assertThat(response.postingId()).isEqualTo(POSTING_ID);
            assertThat(response.bookmarked()).isFalse();
            verify(bookmarkRepository).delete(bookmark);
        }
    }

    @Test
    @DisplayName("removeBookmark throws BOOKMARK_NOT_FOUND when no bookmark exists")
    void removeBookmark_throwsBookmarkNotFound_whenMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(bookmarkRepository.findByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookmarkService.removeBookmark(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKMARK_NOT_FOUND);

            verify(bookmarkRepository, never()).delete(any());
        }
    }
}
