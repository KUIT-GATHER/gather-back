package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.event.BadgeAwardRequestedEvent;
import com.gather.gather.domain.posting.dto.BookmarkResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POSTING_ID = 10L;

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private BookmarkService bookmarkService;

    @BeforeEach
    void setUp() {
        bookmarkService =
                new BookmarkService(
                        bookmarkRepository,
                        postingRepository,
                        new RegionNameResolver(regionRepository),
                        eventPublisher);
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
    @DisplayName("addBookmark publishes BOOKMARK_5 exactly when the fifth bookmark is added (H-3)")
    void addBookmark_publishesBookmark5Event_whenCountReachesFive() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.existsById(POSTING_ID)).thenReturn(true);
            when(bookmarkRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);
            when(bookmarkRepository.countByUserId(USER_ID)).thenReturn(5L);

            bookmarkService.addBookmark(POSTING_ID);

            verify(eventPublisher)
                    .publishEvent(new BadgeAwardRequestedEvent(USER_ID, BadgeType.BOOKMARK_5));
        }
    }

    @Test
    @DisplayName(
            "addBookmark does not publish BOOKMARK_5 when the count is only four (M-11 boundary)")
    void addBookmark_doesNotPublishBookmark5Event_whenCountIsFour() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.existsById(POSTING_ID)).thenReturn(true);
            when(bookmarkRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);
            when(bookmarkRepository.countByUserId(USER_ID)).thenReturn(4L);

            bookmarkService.addBookmark(POSTING_ID);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    @DisplayName(
            "addBookmark does not re-publish BOOKMARK_5 once the count has already passed five"
                    + " (L-9)")
    void addBookmark_doesNotRepublishBookmark5Event_whenCountExceedsFive() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.existsById(POSTING_ID)).thenReturn(true);
            when(bookmarkRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);
            when(bookmarkRepository.countByUserId(USER_ID)).thenReturn(6L);

            bookmarkService.addBookmark(POSTING_ID);

            verify(eventPublisher, never()).publishEvent(any());
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

    @Test
    @DisplayName(
            "getBookmarkedPostings returns bookmarked postings with resolved region names when"
                    + " the given Pageable is unsorted")
    void getBookmarkedPostings_returnsPostingsWithRegionNames_whenUnsorted() {
        Posting posting = posting();
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(1L);
        when(region.getName()).thenReturn("동구");
        Pageable unsortedPageable = PageRequest.of(0, 20);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(bookmarkRepository.findBookmarkedPostings(
                            eq(USER_ID),
                            eq(PostingCategory.ENVIRONMENT),
                            eq("정화"),
                            argThat(p -> p.getSort().isUnsorted())))
                    .thenReturn(new PageImpl<>(List.of(posting), PageRequest.of(0, 20), 1));
            when(regionRepository.findAllById(any())).thenReturn(List.of(region));

            PageResponse<PostingSummaryResponse> response =
                    bookmarkService.getBookmarkedPostings(
                            PostingCategory.ENVIRONMENT, "정화", unsortedPageable);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).regionName()).isEqualTo("동구");
            verify(bookmarkRepository)
                    .findBookmarkedPostings(
                            eq(USER_ID),
                            eq(PostingCategory.ENVIRONMENT),
                            eq("정화"),
                            argThat(p -> p.getSort().isUnsorted()));
        }
    }

    @Test
    @DisplayName(
            "getBookmarkedPostings escapes LIKE wildcard characters in the keyword before"
                    + " querying the repository")
    void getBookmarkedPostings_escapesLikeWildcardsInKeyword() {
        Pageable unsortedPageable = PageRequest.of(0, 20);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(bookmarkRepository.findBookmarkedPostings(
                            eq(USER_ID), isNull(), eq("100\\%"), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            bookmarkService.getBookmarkedPostings(null, "100%", unsortedPageable);

            verify(bookmarkRepository)
                    .findBookmarkedPostings(eq(USER_ID), isNull(), eq("100\\%"), any());
        }
    }

    @Test
    @DisplayName("getBookmarkedPostings throws VALIDATION_ERROR when the client requests a sort")
    void getBookmarkedPostings_throwsValidationError_whenSortRequested() {
        Pageable sortedPageable = PageRequest.of(0, 20, Sort.by("title"));

        assertThatThrownBy(() -> bookmarkService.getBookmarkedPostings(null, null, sortedPageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(bookmarkRepository, never()).findBookmarkedPostings(any(), any(), any(), any());
    }

    private Posting posting() {
        Posting posting =
                Posting.builder()
                        .title("동구 환경정화 봉사")
                        .status(PostingStatus.RECRUITING)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .category(PostingCategory.ENVIRONMENT)
                        .regionId(1L)
                        .build();
        ReflectionTestUtils.setField(posting, "id", POSTING_ID);
        return posting;
    }
}
