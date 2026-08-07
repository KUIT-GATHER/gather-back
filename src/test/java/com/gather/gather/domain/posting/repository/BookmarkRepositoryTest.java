package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.dto.BookmarkedPostingDeadlineTarget;
import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.util.LikeKeywordEscaper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    @Autowired private UserRepository userRepository;
    @Autowired private PostingRepository postingRepository;
    @Autowired private RegionRepository regionRepository;

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
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, null, false, List.of(-1L), null, null, PageRequest.of(0, 20));

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
                        1L,
                        PostingCategory.EDUCATION,
                        null,
                        false,
                        List.of(-1L),
                        null,
                        null,
                        PageRequest.of(0, 20));

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
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, "환경정화", false, List.of(-1L), null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Posting::getId).containsExactly(matching.getId());
    }

    @Test
    void findBookmarkedPostings_returnsEmptyPage_whenUserHasNoBookmarks() {
        postingRepository.save(posting());

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, null, false, List.of(-1L), null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findBookmarkedPostings_filtersByKeyword_matchingRecruitOrgOnly() {
        Posting matching =
                postingRepository.save(posting("무관한 제목", "동구청 환경정화팀", PostingCategory.ENVIRONMENT));
        Posting nonMatching =
                postingRepository.save(posting("무관한 제목", "타 기관", PostingCategory.ENVIRONMENT));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, matching.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, nonMatching.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, "환경정화", false, List.of(-1L), null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Posting::getId).containsExactly(matching.getId());
    }

    @Test
    void findBookmarkedPostings_treatsPercentAsLiteralInKeyword() {
        Posting percentLiteral =
                postingRepository.save(posting("100% 참여", PostingCategory.ENVIRONMENT));
        Posting wildcardExpansion =
                postingRepository.save(posting("1000000 참여", PostingCategory.ENVIRONMENT));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, percentLiteral.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, wildcardExpansion.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(
                        1L,
                        null,
                        LikeKeywordEscaper.escape("100%"),
                        false,
                        List.of(-1L),
                        null,
                        null,
                        PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(Posting::getId)
                .containsExactly(percentLiteral.getId());
    }

    @Test
    void findBookmarkedPostings_ordersByIdDesc_asTiebreak_whenBookmarkedAtIsEqual() {
        Posting first = postingRepository.save(posting());
        Posting second = postingRepository.save(posting());
        Bookmark firstBookmark = Bookmark.create(1L, first.getId());
        Bookmark secondBookmark = Bookmark.create(1L, second.getId());
        LocalDateTime sameInstant = LocalDateTime.of(2026, 7, 1, 0, 0);
        ReflectionTestUtils.setField(firstBookmark, "createdAt", sameInstant);
        ReflectionTestUtils.setField(secondBookmark, "createdAt", sameInstant);
        bookmarkRepository.saveAndFlush(firstBookmark);
        bookmarkRepository.saveAndFlush(secondBookmark);

        Page<Posting> firstPage =
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, null, false, List.of(-1L), null, null, PageRequest.of(0, 1));
        Page<Posting> secondPage =
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, null, false, List.of(-1L), null, null, PageRequest.of(1, 1));

        assertThat(firstPage.getContent())
                .extracting(Posting::getId)
                .containsExactly(second.getId());
        assertThat(secondPage.getContent())
                .extracting(Posting::getId)
                .containsExactly(first.getId());
    }

    @Test
    void findBookmarkedPostings_filtersByCategoryAndKeywordTogether() {
        Posting matching =
                postingRepository.save(posting("동구 환경정화 봉사", PostingCategory.ENVIRONMENT));
        Posting wrongCategory =
                postingRepository.save(posting("동구 환경정화 봉사", PostingCategory.EDUCATION));
        Posting wrongKeyword =
                postingRepository.save(posting("무관한 제목", PostingCategory.ENVIRONMENT));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, matching.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, wrongCategory.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, wrongKeyword.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(
                        1L,
                        PostingCategory.ENVIRONMENT,
                        "환경정화",
                        false,
                        List.of(-1L),
                        null,
                        null,
                        PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Posting::getId).containsExactly(matching.getId());
    }

    @Test
    void findBookmarkedPostings_paginatesAcrossMultiplePages() {
        for (int i = 0; i < 3; i++) {
            Posting posting = postingRepository.save(posting());
            bookmarkRepository.saveAndFlush(Bookmark.create(1L, posting.getId()));
        }

        Page<Posting> firstPage =
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, null, false, List.of(-1L), null, null, PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        Page<Posting> secondPage =
                bookmarkRepository.findBookmarkedPostings(
                        1L, null, null, false, List.of(-1L), null, null, PageRequest.of(1, 2));

        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findBookmarkedPostings_filtersByRegionIncludingChildren() {
        Region parentRegion = regionRepository.save(Region.create("서울", 1, "seoul-region", null));
        Region childRegion =
                regionRepository.save(Region.create("강남구", 2, "gangnam-region", parentRegion));
        Region otherRegion = regionRepository.save(Region.create("부산", 1, "busan-region", null));

        Posting inChildRegion = postingRepository.save(postingInRegion(childRegion.getId()));
        Posting inOtherRegion = postingRepository.save(postingInRegion(otherRegion.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, inChildRegion.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, inOtherRegion.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(
                        1L,
                        null,
                        null,
                        true,
                        List.of(parentRegion.getId(), childRegion.getId()),
                        null,
                        null,
                        PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(Posting::getId)
                .containsExactly(inChildRegion.getId());
    }

    @Test
    void findBookmarkedPostings_filtersByNoticeDateRange() {
        Posting inRange =
                postingRepository.save(
                        postingWithNoticePeriod(
                                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20)));
        Posting startsBeforeRange =
                postingRepository.save(
                        postingWithNoticePeriod(
                                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20)));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, inRange.getId()));
        bookmarkRepository.saveAndFlush(Bookmark.create(1L, startsBeforeRange.getId()));

        Page<Posting> page =
                bookmarkRepository.findBookmarkedPostings(
                        1L,
                        null,
                        null,
                        false,
                        List.of(-1L),
                        LocalDate.of(2026, 7, 5),
                        LocalDate.of(2026, 7, 25),
                        PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Posting::getId).containsExactly(inRange.getId());
    }

    @Test
    void findPostingDeadlineNotificationTargets_returnsOnlyPostingEndingOnDeadlineDate() {
        LocalDate deadlineDate = LocalDate.of(2026, 8, 5);
        User user = activeUser();

        Posting matching =
                postingRepository.save(
                        deadlinePosting(
                                "마감 3일 전 공고", deadlineDate, PostingStatus.RECRUITING, true));

        Posting oneDayEarlier =
                postingRepository.save(
                        deadlinePosting(
                                "하루 빠른 공고",
                                deadlineDate.minusDays(1),
                                PostingStatus.RECRUITING,
                                true));

        Posting oneDayLater =
                postingRepository.save(
                        deadlinePosting(
                                "하루 늦은 공고",
                                deadlineDate.plusDays(1),
                                PostingStatus.RECRUITING,
                                true));

        bookmarkRepository.save(Bookmark.create(user.getId(), matching.getId()));
        bookmarkRepository.save(Bookmark.create(user.getId(), oneDayEarlier.getId()));
        bookmarkRepository.save(Bookmark.create(user.getId(), oneDayLater.getId()));
        bookmarkRepository.flush();

        List<BookmarkedPostingDeadlineTarget> result =
                bookmarkRepository.findPostingDeadlineNotificationTargets(deadlineDate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(user.getId());
        assertThat(result.get(0).postingId()).isEqualTo(matching.getId());
        assertThat(result.get(0).postingTitle()).isEqualTo("마감 3일 전 공고");
    }

    @Test
    void findPostingDeadlineNotificationTargets_excludesInactiveAndNullActivePosting() {
        LocalDate deadlineDate = LocalDate.of(2026, 8, 5);
        User user = activeUser();

        Posting inactive =
                postingRepository.save(
                        deadlinePosting("비활성화 공고", deadlineDate, PostingStatus.RECRUITING, false));

        Posting nullActive =
                postingRepository.save(
                        deadlinePosting(
                                "활성 상태가 없는 공고", deadlineDate, PostingStatus.RECRUITING, null));

        bookmarkRepository.save(Bookmark.create(user.getId(), inactive.getId()));
        bookmarkRepository.save(Bookmark.create(user.getId(), nullActive.getId()));
        bookmarkRepository.flush();

        List<BookmarkedPostingDeadlineTarget> result =
                bookmarkRepository.findPostingDeadlineNotificationTargets(deadlineDate);

        assertThat(result).isEmpty();
    }

    @Test
    void findPostingDeadlineNotificationTargets_excludesNonRecruitingPosting() {
        LocalDate deadlineDate = LocalDate.of(2026, 8, 5);
        User user = activeUser();

        Posting closed =
                postingRepository.save(
                        deadlinePosting("마감된 공고", deadlineDate, PostingStatus.CLOSED, true));

        Posting completed =
                postingRepository.save(
                        deadlinePosting("완료된 공고", deadlineDate, PostingStatus.COMPLETED, true));

        bookmarkRepository.save(Bookmark.create(user.getId(), closed.getId()));
        bookmarkRepository.save(Bookmark.create(user.getId(), completed.getId()));
        bookmarkRepository.flush();

        List<BookmarkedPostingDeadlineTarget> result =
                bookmarkRepository.findPostingDeadlineNotificationTargets(deadlineDate);

        assertThat(result).isEmpty();
    }

    @Test
    void findPostingDeadlineNotificationTargets_returnsOnlyActiveUser() {
        LocalDate deadlineDate = LocalDate.of(2026, 8, 5);

        User activeUser = userWithStatus(UserStatus.ACTIVE);
        User suspendedUser = userWithStatus(UserStatus.SUSPENDED);
        User withdrawalPendingUser = userWithStatus(UserStatus.WITHDRAWAL_PENDING);
        User withdrawnUser = userWithStatus(UserStatus.WITHDRAWN);

        Posting posting =
                postingRepository.save(
                        deadlinePosting("마감 임박 공고", deadlineDate, PostingStatus.RECRUITING, true));

        bookmarkRepository.save(Bookmark.create(activeUser.getId(), posting.getId()));
        bookmarkRepository.save(Bookmark.create(suspendedUser.getId(), posting.getId()));
        bookmarkRepository.save(Bookmark.create(withdrawalPendingUser.getId(), posting.getId()));
        bookmarkRepository.save(Bookmark.create(withdrawnUser.getId(), posting.getId()));
        bookmarkRepository.flush();

        List<BookmarkedPostingDeadlineTarget> result =
                bookmarkRepository.findPostingDeadlineNotificationTargets(deadlineDate);

        assertThat(result)
                .extracting(BookmarkedPostingDeadlineTarget::userId)
                .containsExactly(activeUser.getId());
    }

    private Posting posting() {
        return posting("테스트 공고", PostingCategory.ENVIRONMENT);
    }

    private Posting posting(String title, PostingCategory category) {
        return posting(title, null, category);
    }

    private Posting posting(String title, String recruitOrg, PostingCategory category) {
        return Posting.builder()
                .title(title)
                .recruitOrg(recruitOrg)
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(category)
                .source(PostingSource.API_1365)
                .build();
    }

    private Posting postingInRegion(Long regionId) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(PostingCategory.ENVIRONMENT)
                .regionId(regionId)
                .source(PostingSource.API_1365)
                .build();
    }

    private Posting postingWithNoticePeriod(LocalDate noticeStartDate, LocalDate noticeEndDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(PostingCategory.ENVIRONMENT)
                .noticeStartDate(noticeStartDate)
                .noticeEndDate(noticeEndDate)
                .source(PostingSource.API_1365)
                .build();
    }

    private Posting deadlinePosting(
            String title, LocalDate noticeEndDate, PostingStatus status, Boolean isActive) {
        return Posting.builder()
                .title(title)
                .status(status)
                .activityDate(LocalDate.of(2026, 9, 1))
                .noticeEndDate(noticeEndDate)
                .isActive(isActive)
                .category(PostingCategory.ENVIRONMENT)
                .source(PostingSource.API_1365)
                .build();
    }

    private User activeUser() {
        return userWithStatus(UserStatus.ACTIVE);
    }

    private User userWithStatus(UserStatus status) {
        long suffix = Math.abs(System.nanoTime() % 1_000_000_000L);

        Region region =
                regionRepository.save(Region.create("알림테스트구", 2, "deadline-" + suffix, null));

        User user =
                User.create(
                        "알림 테스트 사용자",
                        LocalDate.of(1995, 1, 1),
                        Gender.MALE,
                        "010123" + suffix,
                        null,
                        null,
                        "deadline" + suffix,
                        null,
                        true,
                        true,
                        false,
                        region,
                        List.of());

        ReflectionTestUtils.setField(user, "status", status);

        return userRepository.save(user);
    }
}
