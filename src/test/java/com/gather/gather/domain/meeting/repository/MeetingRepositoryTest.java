package com.gather.gather.domain.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MeetingRepositoryTest {

    @Autowired private MeetingRepository meetingRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private RegionRepository regionRepository;

    @Test
    void searchMeetings_includesMeetingWithoutActivityPeriod_whenRecruitingOnlyIsTrue() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
        Region region = region();
        Meeting meeting = meetingRepository.save(meeting(region, now, null, null));

        Page<Meeting> result =
                meetingRepository.searchMeetings(
                        null,
                        false,
                        List.of(region.getId()),
                        null,
                        MeetingStatus.RECRUITING,
                        true,
                        now,
                        null,
                        null,
                        false,
                        PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Meeting::getId).contains(meeting.getId());
    }

    @Test
    void searchMeetings_excludesMeetingWithoutActivityPeriod_whenActivityPeriodFilterIsApplied() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
        LocalDateTime filterStartAt = now.plusDays(4);
        LocalDateTime filterEndAt = now.plusDays(6);
        Region region = region();
        Meeting meetingWithoutActivityPeriod =
                meetingRepository.save(meeting(region, now, null, null));
        Meeting meetingOverlappingActivityPeriod =
                meetingRepository.save(
                        meeting(region, now, now.plusDays(5), now.plusDays(5).plusHours(2)));

        Page<Meeting> result =
                meetingRepository.searchMeetings(
                        null,
                        false,
                        List.of(region.getId()),
                        null,
                        null,
                        false,
                        now,
                        filterStartAt,
                        filterEndAt,
                        false,
                        PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(Meeting::getId)
                .contains(meetingOverlappingActivityPeriod.getId())
                .doesNotContain(meetingWithoutActivityPeriod.getId());
    }

    private Meeting meeting(
            Region region,
            LocalDateTime now,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt) {
        return Meeting.create(
                "리포지토리 테스트 모임",
                "활동 기간 조회 조건을 검증합니다.",
                10,
                now.plusDays(3),
                null,
                Set.of(PostingCategory.ENVIRONMENT),
                region.getId(),
                userRepository.save(user(region)),
                "누구나 참여할 수 있습니다.",
                null,
                activityStartAt,
                activityEndAt);
    }

    private Region region() {
        return regionRepository.save(
                Region.create("테스트구", 2, "998" + (System.nanoTime() % 10_000_000L), null));
    }

    private User user(Region region) {
        String suffix = String.valueOf(System.nanoTime());
        String uniqueSuffix = suffix.substring(Math.max(0, suffix.length() - 8));
        return User.create(
                "repo-user",
                LocalDate.of(1995, 1, 1),
                Gender.MALE,
                "010" + uniqueSuffix,
                null,
                null,
                "repo-" + uniqueSuffix,
                null,
                true,
                true,
                false,
                region,
                List.of());
    }
}
