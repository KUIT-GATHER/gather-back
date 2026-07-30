package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.config.RecommendationProperties;
import com.gather.gather.global.util.CategoryDeadlineScoreCalculator;
import com.gather.gather.global.util.PreferredCategoryResolver;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingRecommendationServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;

    private MeetingRecommendationService meetingRecommendationService;

    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        meetingRecommendationService =
                new MeetingRecommendationService(
                        meetingRepository,
                        meetingMemberRepository,
                        new PreferredCategoryResolver(userRepository),
                        new CategoryDeadlineScoreCalculator(
                                new RecommendationProperties(0.7, 0.3, 30)));
    }

    @Test
    @DisplayName(
            "getRecommendedMeetings ranks by category match then deadline proximity, excluding "
                    + "meetings already joined (APPROVED) or pending (PENDING)")
    void getRecommendedMeetings_ranksByScoreAndExcludesJoinedOrPending() {
        Meeting m1 = meeting(1L, PostingCategory.ENVIRONMENT, now.plusDays(5));
        Meeting m2 = meeting(2L, PostingCategory.WELFARE, now.plusDays(1));
        Meeting m3 = meeting(3L, PostingCategory.ENVIRONMENT, now.plusDays(40));
        Meeting m4 = meeting(4L, PostingCategory.WELFARE, now.plusDays(20));
        Meeting m5 = meeting(5L, PostingCategory.EDUCATION, now.plusDays(2));
        Meeting m6 = meeting(6L, PostingCategory.ENVIRONMENT, now); // 이미 가입(APPROVED)한 모임 → 제외되어야 함
        Meeting m7 =
                meeting(7L, PostingCategory.ENVIRONMENT, now); // 가입 신청 중(PENDING)인 모임 → 제외되어야 함

        when(meetingRepository.searchMeetings(
                        isNull(),
                        eq(false),
                        eq(List.of(-1L)),
                        isNull(),
                        isNull(),
                        eq(true),
                        any(LocalDateTime.class),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1, m2, m3, m4, m5, m6, m7)));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(userWithPreference(PostingCategory.ENVIRONMENT)));
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        eq(USER_ID), eq(MeetingMemberStatus.PENDING)))
                .thenReturn(List.of(joinedMember(m7)));
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        eq(USER_ID), eq(MeetingMemberStatus.APPROVED)))
                .thenReturn(List.of(joinedMember(m6)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(USER_ID);

            List<MeetingResponse> recommended =
                    meetingRecommendationService.getRecommendedMeetings();

            // m1(cat+near) > m3(cat, far) > m2(no cat, near) > m5(no cat) > m4(no cat, far).
            // m6(APPROVED)·m7(PENDING) 제외.
            assertThat(recommended)
                    .extracting(MeetingResponse::meetingId)
                    .containsExactly(1L, 3L, 2L, 5L, 4L);
        }
    }

    @Test
    @DisplayName(
            "getRecommendedMeetings falls back to nearest-deadline order for a guest (no login)")
    void getRecommendedMeetings_guestFallsBackToDeadlineOrder() {
        Meeting m1 = meeting(1L, PostingCategory.ENVIRONMENT, now.plusDays(5));
        Meeting m2 = meeting(2L, PostingCategory.WELFARE, now.plusDays(1));
        Meeting m3 = meeting(3L, PostingCategory.ENVIRONMENT, now.plusDays(40));
        Meeting m4 = meeting(4L, PostingCategory.WELFARE, now.plusDays(20));
        Meeting m5 = meeting(5L, PostingCategory.EDUCATION, now.plusDays(2));

        when(meetingRepository.searchMeetings(
                        isNull(),
                        eq(false),
                        eq(List.of(-1L)),
                        isNull(),
                        isNull(),
                        eq(true),
                        any(LocalDateTime.class),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1, m2, m3, m4, m5)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(null);

            List<MeetingResponse> recommended =
                    meetingRecommendationService.getRecommendedMeetings();

            assertThat(recommended)
                    .extracting(MeetingResponse::meetingId)
                    .containsExactly(2L, 5L, 1L, 4L, 3L);
        }
    }

    @Test
    @DisplayName(
            "getRecommendedMeetings returns fewer than 5 items when the candidate pool is smaller")
    void getRecommendedMeetings_smallCandidatePool_returnsAllOfThem() {
        Meeting m1 = meeting(1L, PostingCategory.ENVIRONMENT, now.plusDays(1));
        Meeting m2 = meeting(2L, PostingCategory.WELFARE, now.plusDays(5));

        when(meetingRepository.searchMeetings(
                        isNull(),
                        eq(false),
                        eq(List.of(-1L)),
                        isNull(),
                        isNull(),
                        eq(true),
                        any(LocalDateTime.class),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1, m2)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(null);

            List<MeetingResponse> recommended =
                    meetingRecommendationService.getRecommendedMeetings();

            assertThat(recommended).extracting(MeetingResponse::meetingId).containsExactly(1L, 2L);
        }
    }

    @Test
    @DisplayName(
            "getRecommendedMeetings returns an empty list when every candidate is already joined or pending")
    void getRecommendedMeetings_allCandidatesExcluded_returnsEmptyList() {
        Meeting m1 = meeting(1L, PostingCategory.ENVIRONMENT, now.plusDays(1));
        Meeting m2 = meeting(2L, PostingCategory.WELFARE, now.plusDays(5));

        when(meetingRepository.searchMeetings(
                        isNull(),
                        eq(false),
                        eq(List.of(-1L)),
                        isNull(),
                        isNull(),
                        eq(true),
                        any(LocalDateTime.class),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1, m2)));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(userWithPreference(PostingCategory.ENVIRONMENT)));
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        eq(USER_ID), eq(MeetingMemberStatus.PENDING)))
                .thenReturn(List.of(joinedMember(m1)));
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        eq(USER_ID), eq(MeetingMemberStatus.APPROVED)))
                .thenReturn(List.of(joinedMember(m2)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(USER_ID);

            List<MeetingResponse> recommended =
                    meetingRecommendationService.getRecommendedMeetings();

            assertThat(recommended).isEmpty();
        }
    }

    @Test
    @DisplayName("getRecommendedMeetings breaks ties on equal score and deadline by ascending id")
    void getRecommendedMeetings_tiedScoreAndDeadline_breaksTieByAscendingId() {
        LocalDateTime sameDeadline = now.plusDays(10);
        // 두 후보 모두 선호 카테고리(ENVIRONMENT)와 매칭되지 않아 카테고리 점수 0, 마감일도 동일 → 총점 동점.
        Meeting higherId = meeting(20L, PostingCategory.WELFARE, sameDeadline);
        Meeting lowerId = meeting(10L, PostingCategory.WELFARE, sameDeadline);

        when(meetingRepository.searchMeetings(
                        isNull(),
                        eq(false),
                        eq(List.of(-1L)),
                        isNull(),
                        isNull(),
                        eq(true),
                        any(LocalDateTime.class),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(higherId, lowerId)));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(userWithPreference(PostingCategory.ENVIRONMENT)));
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        eq(USER_ID), any(MeetingMemberStatus.class)))
                .thenReturn(List.of());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(USER_ID);

            List<MeetingResponse> recommended =
                    meetingRecommendationService.getRecommendedMeetings();

            assertThat(recommended)
                    .extracting(MeetingResponse::meetingId)
                    .containsExactly(10L, 20L);
        }
    }

    @Test
    @DisplayName(
            "getRecommendedMeetings scans beyond the first page so a preferred-category meeting"
                    + " on a later page still outranks a non-preferred meeting on the first page")
    void getRecommendedMeetings_scansSecondPage_preferredCategoryOnLaterPageWins() {
        Meeting nearNonPreferred = meeting(1L, PostingCategory.WELFARE, now.plusDays(1));
        Meeting farPreferred = meeting(2L, PostingCategory.ENVIRONMENT, now.plusDays(29));

        Page<Meeting> firstPage =
                new PageImpl<>(List.of(nearNonPreferred), PageRequest.of(0, 1), 2);
        Page<Meeting> secondPage = new PageImpl<>(List.of(farPreferred), PageRequest.of(1, 1), 2);
        when(meetingRepository.searchMeetings(
                        isNull(),
                        eq(false),
                        eq(List.of(-1L)),
                        isNull(),
                        isNull(),
                        eq(true),
                        any(LocalDateTime.class),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(firstPage, secondPage);
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(userWithPreference(PostingCategory.ENVIRONMENT)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(USER_ID);

            List<MeetingResponse> recommended =
                    meetingRecommendationService.getRecommendedMeetings();

            assertThat(recommended).extracting(MeetingResponse::meetingId).containsExactly(2L, 1L);
        }
    }

    private Meeting meeting(Long id, PostingCategory category, LocalDateTime deadline) {
        Meeting createdMeeting =
                Meeting.create(
                        "테스트 모임 " + id,
                        "설명",
                        10,
                        deadline,
                        null,
                        Set.of(category),
                        1L,
                        dummyHost(),
                        null,
                        null,
                        now.plusDays(50),
                        now.plusDays(50).plusHours(2));
        ReflectionTestUtils.setField(createdMeeting, "id", id);
        return createdMeeting;
    }

    private MeetingMember joinedMember(Meeting meeting) {
        return MeetingMember.createMember(dummyHost(), meeting);
    }

    private User dummyHost() {
        return User.create(
                "호스트",
                LocalDate.of(1995, 1, 1),
                Gender.FEMALE,
                "01098765432",
                "host@example.com",
                "encoded-password",
                "호스트닉네임",
                "소개",
                true,
                true,
                false,
                null,
                List.of());
    }

    private User userWithPreference(PostingCategory category) {
        User createdUser =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        "소개",
                        true,
                        true,
                        false,
                        null,
                        List.of(category));
        ReflectionTestUtils.setField(createdUser, "id", USER_ID);
        return createdUser;
    }
}
