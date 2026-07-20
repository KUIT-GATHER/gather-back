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
        Meeting meeting = meetingRepository.save(meeting());
        meetingBookmarkRepository.save(MeetingBookmark.create(1L, meeting.getId()));

        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(1L, meeting.getId()))
                .isTrue();
    }

    @Test
    void existsByUserIdAndMeetingId_returnsFalse_whenBookmarkDoesNotExist() {
        Meeting meeting = meetingRepository.save(meeting());

        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(1L, meeting.getId()))
                .isFalse();
    }

    @Test
    void findByUserIdAndMeetingId_returnsBookmark_whenExists() {
        Meeting meeting = meetingRepository.save(meeting());
        MeetingBookmark saved =
                meetingBookmarkRepository.save(MeetingBookmark.create(1L, meeting.getId()));

        assertThat(meetingBookmarkRepository.findByUserIdAndMeetingId(1L, meeting.getId()))
                .contains(saved);
    }

    @Test
    void findByUserIdAndMeetingId_returnsEmpty_whenNotExists() {
        Meeting meeting = meetingRepository.save(meeting());

        assertThat(meetingBookmarkRepository.findByUserIdAndMeetingId(1L, meeting.getId()))
                .isEmpty();
    }

    @Test
    void save_throwsDataIntegrityViolationException_whenUserAndMeetingAlreadyBookmarked() {
        Meeting meeting = meetingRepository.save(meeting());
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(1L, meeting.getId()));

        assertThatThrownBy(
                        () ->
                                meetingBookmarkRepository.saveAndFlush(
                                        MeetingBookmark.create(1L, meeting.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_allowsSameUserToBookmarkDifferentMeetings() {
        Meeting first = meetingRepository.save(meeting());
        Meeting second = meetingRepository.save(meeting());

        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(1L, first.getId()));
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(1L, second.getId()));

        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(1L, first.getId()))
                .isTrue();
        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(1L, second.getId()))
                .isTrue();
    }

    @Test
    void save_allowsDifferentUsersToBookmarkSameMeeting() {
        Meeting meeting = meetingRepository.save(meeting());

        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(1L, meeting.getId()));
        meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(2L, meeting.getId()));

        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(1L, meeting.getId()))
                .isTrue();
        assertThat(meetingBookmarkRepository.existsByUserIdAndMeetingId(2L, meeting.getId()))
                .isTrue();
    }

    private Meeting meeting() {
        Region region =
                regionRepository.save(
                        Region.create("테스트구", 2, "999" + (System.nanoTime() % 10000000L), null));
        User host = userRepository.save(host(region));
        LocalDateTime now = LocalDateTime.now();

        return Meeting.create(
                "테스트 모임",
                "설명",
                10,
                now.plusDays(3),
                null,
                PostingCategory.ENVIRONMENT,
                region.getId(),
                host,
                null,
                null,
                now.plusDays(5),
                now.plusDays(5).plusHours(2));
    }

    private User host(Region region) {
        return User.create(
                "호스트",
                LocalDate.of(1995, 1, 1),
                Gender.MALE,
                "010" + System.nanoTime() % 100000000L,
                null,
                null,
                "host-" + System.nanoTime(),
                null,
                true,
                true,
                false,
                region,
                List.of());
    }
}
