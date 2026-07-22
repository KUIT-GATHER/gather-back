package com.gather.gather.domain.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
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
 * {@code uk_meeting_bookmark_user_meeting} DB 유니크 제약이 실제로 (user_id, meeting_id) 중복 저장을 막는지 검증한다.
 * {@link com.gather.gather.domain.meeting.service.MeetingBookmarkService}는 이 제약을 동시 요청 방어 최후 수단으로
 * 의존하므로, 목(mock) 리포지토리가 아닌 실제 DB 레벨에서 확인이 필요하다.
 */
@SpringBootTest
@Transactional
class MeetingBookmarkRepositoryTest {

    @Autowired private MeetingBookmarkRepository meetingBookmarkRepository;

    @Autowired private MeetingRepository meetingRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private RegionRepository regionRepository;

    @Test
    void existsByUserIdAndMeetingId_returnsTrue_whenBookmarkExists() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();
        meetingBookmarkRepository.save(MeetingBookmark.create(userId, meeting.getId()));

        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(userId, meeting.getId()))
                .isTrue();
    }

    @Test
    void existsByUserIdAndMeetingId_returnsFalse_whenBookmarkDoesNotExist() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();

        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(userId, meeting.getId()))
                .isFalse();
    }

    @Test
    void findByUserIdAndMeetingId_returnsBookmark_whenExists() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();
        MeetingBookmark saved =
                meetingBookmarkRepository.save(MeetingBookmark.create(userId, meeting.getId()));

        assertThat(meetingBookmarkRepository.findByUserIdAndMeetingId(userId, meeting.getId()))
                .contains(saved);
    }

    @Test
    void findByUserIdAndMeetingId_returnsEmpty_whenNotExists() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();

        assertThat(meetingBookmarkRepository.findByUserIdAndMeetingId(userId, meeting.getId()))
                .isEmpty();
    }

    @Test
    void save_throwsDataIntegrityViolationException_whenUserAndMeetingAlreadyBookmarked() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, meeting.getId()));

        assertThatThrownBy(
                        () ->
                                meetingBookmarkRepository.saveAndFlush(
                                        MeetingBookmark.create(userId, meeting.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_allowsSameUserToBookmarkDifferentMeetings() {
        Region region = region();
        Meeting first = meetingRepository.save(meeting(region));
        Meeting second = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();

        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, first.getId()));
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, second.getId()));

        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(userId, first.getId()))
                .isTrue();
        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(userId, second.getId()))
                .isTrue();
    }

    @Test
    void save_allowsDifferentUsersToBookmarkSameMeeting() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long firstUserId = bookmarker(region).getId();
        Long secondUserId = bookmarker(region).getId();

        meetingBookmarkRepository.saveAndFlush(
                MeetingBookmark.create(firstUserId, meeting.getId()));
        meetingBookmarkRepository.saveAndFlush(
                MeetingBookmark.create(secondUserId, meeting.getId()));

        assertThat(
                        meetingBookmarkRepository.existsByUserIdAndMeetingId(
                                firstUserId, meeting.getId()))
                .isTrue();
        assertThat(
                        meetingBookmarkRepository.existsByUserIdAndMeetingId(
                                secondUserId, meeting.getId()))
                .isTrue();
    }

    @Test
    void deleteByUserIdAndMeetingId_deletesBookmark_andReturnsOne() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, meeting.getId()));

        int deletedCount =
                meetingBookmarkRepository.deleteByUserIdAndMeetingId(userId, meeting.getId());

        assertThat(deletedCount).isEqualTo(1);
        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(userId, meeting.getId()))
                .isFalse();
    }

    @Test
    void deleteByUserIdAndMeetingId_returnsZero_whenBookmarkDoesNotExist() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();

        int deletedCount =
                meetingBookmarkRepository.deleteByUserIdAndMeetingId(userId, meeting.getId());

        assertThat(deletedCount).isZero();
    }

    @Test
    void findBookmarkedMeetings_returnsOnlyThatUsersBookmarks_orderedByBookmarkedAtDesc() {
        Region region = region();
        Meeting first = meetingRepository.save(meeting(region));
        Meeting second = meetingRepository.save(meeting(region));
        Meeting othersMeeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();
        Long otherUserId = bookmarker(region).getId();
        MeetingBookmark firstBookmark = MeetingBookmark.create(userId, first.getId());
        MeetingBookmark secondBookmark = MeetingBookmark.create(userId, second.getId());
        // 두 저장 호출 사이 실제 경과 시간에 의존하면 클럭 해상도에 따라 흔들릴 수 있어, 북마크 시각을 명시적으로 벌려
        // "나중에 북마크한 것이 먼저 나온다"는 정렬 규칙만 결정적으로 검증한다.
        ReflectionTestUtils.setField(
                firstBookmark, "createdAt", LocalDateTime.of(2026, 7, 1, 0, 0));
        ReflectionTestUtils.setField(
                secondBookmark, "createdAt", LocalDateTime.of(2026, 7, 2, 0, 0));
        meetingBookmarkRepository.saveAndFlush(firstBookmark);
        meetingBookmarkRepository.saveAndFlush(secondBookmark);
        meetingBookmarkRepository.saveAndFlush(
                MeetingBookmark.create(otherUserId, othersMeeting.getId()));

        Page<Meeting> page =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(Meeting::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findBookmarkedMeetings_filtersByCategory() {
        Region region = region();
        Meeting environment = meetingRepository.save(meeting(region, PostingCategory.ENVIRONMENT));
        Meeting education = meetingRepository.save(meeting(region, PostingCategory.EDUCATION));
        Long userId = bookmarker(region).getId();
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, environment.getId()));
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, education.getId()));

        Page<Meeting> page =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, PostingCategory.EDUCATION, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Meeting::getId).containsExactly(education.getId());
    }

    @Test
    void findBookmarkedMeetings_excludesDeletedMeeting() {
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, meeting.getId()));
        meeting.delete();
        meetingRepository.saveAndFlush(meeting);

        Page<Meeting> page =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findBookmarkedMeetings_returnsEmptyPage_whenUserHasNoBookmarks() {
        Region region = region();
        meetingRepository.save(meeting(region));
        Long userId = bookmarker(region).getId();

        Page<Meeting> page =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findBookmarkedMeetings_filtersByKeyword() {
        Region region = region();
        Meeting matching = meetingRepository.save(meeting(region, "동구 환경정화 모임"));
        Meeting nonMatching = meetingRepository.save(meeting(region, "무관한 모임"));
        Long userId = bookmarker(region).getId();
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, matching.getId()));
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, nonMatching.getId()));

        Page<Meeting> page =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, null, "환경정화", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Meeting::getId).containsExactly(matching.getId());
    }

    @Test
    void findBookmarkedMeetings_filtersByCategoryAndKeywordTogether() {
        Region region = region();
        Meeting matching =
                meetingRepository.save(meeting(region, "동구 환경정화 모임", PostingCategory.ENVIRONMENT));
        Meeting wrongCategory =
                meetingRepository.save(meeting(region, "동구 환경정화 모임", PostingCategory.EDUCATION));
        Meeting wrongKeyword =
                meetingRepository.save(meeting(region, "무관한 모임", PostingCategory.ENVIRONMENT));
        Long userId = bookmarker(region).getId();
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, matching.getId()));
        meetingBookmarkRepository.saveAndFlush(
                MeetingBookmark.create(userId, wrongCategory.getId()));
        meetingBookmarkRepository.saveAndFlush(
                MeetingBookmark.create(userId, wrongKeyword.getId()));

        Page<Meeting> page =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, PostingCategory.ENVIRONMENT, "환경정화", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Meeting::getId).containsExactly(matching.getId());
    }

    @Test
    void findBookmarkedMeetings_paginatesAcrossMultiplePages() {
        Region region = region();
        Long userId = bookmarker(region).getId();
        for (int i = 0; i < 3; i++) {
            Meeting meeting = meetingRepository.save(meeting(region));
            meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, meeting.getId()));
        }

        Page<Meeting> firstPage =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, null, null, PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        Page<Meeting> secondPage =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, null, null, PageRequest.of(1, 2));

        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
    }

    private Region region() {
        return regionRepository.save(
                Region.create("테스트구", 2, "999" + (System.nanoTime() % 10000000L), null));
    }

    private Meeting meeting(Region region) {
        return meeting(region, PostingCategory.ENVIRONMENT);
    }

    private Meeting meeting(Region region, PostingCategory category) {
        return meeting(region, "테스트 모임", category);
    }

    private Meeting meeting(Region region, String name) {
        return meeting(region, name, PostingCategory.ENVIRONMENT);
    }

    private Meeting meeting(Region region, String name, PostingCategory category) {
        User host = userRepository.save(user("host", region));
        LocalDateTime now = LocalDateTime.now();

        return Meeting.create(
                name,
                "설명",
                10,
                now.plusDays(3),
                null,
                category,
                region.getId(),
                host,
                null,
                null,
                now.plusDays(5),
                now.plusDays(5).plusHours(2));
    }

    private User bookmarker(Region region) {
        return userRepository.save(user("guest", region));
    }

    private User user(String label, Region region) {
        return User.create(
                label,
                LocalDate.of(1995, 1, 1),
                Gender.MALE,
                "010" + System.nanoTime() % 100000000L,
                null,
                null,
                label + "-" + (System.nanoTime() % 10_000_000_000L),
                null,
                true,
                true,
                false,
                region,
                List.of());
    }
}
