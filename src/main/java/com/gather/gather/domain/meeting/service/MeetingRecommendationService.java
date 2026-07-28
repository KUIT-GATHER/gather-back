package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.util.CategoryDeadlineScoreCalculator;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 추천(회원가입 시 선택한 선호 카테고리 + 마감일 근접도 기준 상위 5개).
 *
 * <p>모임 생성/가입/목록조회를 담당하는 {@link MeetingService}와 분리해, 추천 전용 조회·채점·정렬만 담당한다. 점수 산식은 {@link
 * CategoryDeadlineScoreCalculator}를 통해 봉사공고 추천과 동일한 로직을 공유한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingRecommendationService {

    private static final int RECOMMENDATION_COUNT = 5;

    /** 추천 후보 풀 크기. 전량을 메모리에서 채점·정렬하므로 상한을 둔다(카탈로그 규모가 커지면 DB 사전 필터 추가 검토). */
    private static final int CANDIDATE_POOL_SIZE = 200;

    /**
     * {@code MeetingRepository#searchMeetings}는 지역 필터를 끄더라도 empty-IN 방지용 더미값이 필요하다(MeetingService와
     * 동일).
     */
    private static final List<Long> NO_REGION_FILTER = List.of(-1L);

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;
    private final CategoryDeadlineScoreCalculator scoreCalculator;

    public List<MeetingResponse> getRecommendedMeetings() {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        Set<PostingCategory> preferredCategories = resolvePreferredCategories(userId);

        LocalDateTime now = LocalDateTime.now();
        Pageable candidatePage = PageRequest.of(0, CANDIDATE_POOL_SIZE);
        Page<Meeting> candidates =
                meetingRepository.searchMeetings(
                        null,
                        false,
                        NO_REGION_FILTER,
                        null,
                        null,
                        true,
                        now,
                        null,
                        null,
                        null,
                        candidatePage);

        Set<Long> excludedMeetingIds = resolveJoinedMeetingIds(userId, candidates.getContent());

        List<Meeting> ranked =
                candidates.getContent().stream()
                        .filter(meeting -> !excludedMeetingIds.contains(meeting.getId()))
                        .map(
                                meeting ->
                                        new ScoredMeeting(
                                                meeting,
                                                scoreCalculator.score(
                                                        meeting.getCategory(),
                                                        preferredCategories,
                                                        meeting.getDeadline(),
                                                        now)))
                        .sorted(
                                Comparator.comparingDouble(ScoredMeeting::score)
                                        .reversed()
                                        .thenComparing(sm -> sm.meeting().getDeadline())
                                        .thenComparing(sm -> sm.meeting().getId()))
                        .limit(RECOMMENDATION_COUNT)
                        .map(ScoredMeeting::meeting)
                        .toList();

        return ranked.stream()
                .map(meeting -> MeetingResponse.from(meeting, MeetingStatus.RECRUITING))
                .toList();
    }

    private Set<PostingCategory> resolvePreferredCategories(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return userRepository
                .findById(userId)
                .map(user -> Set.copyOf(user.getInterestCategories()))
                .orElse(Set.of());
    }

    /** 이미 가입(APPROVED)했거나 가입 신청 중(PENDING)인 모임은 추천에서 제외한다. */
    private Set<Long> resolveJoinedMeetingIds(Long userId, List<Meeting> candidates) {
        if (userId == null || candidates.isEmpty()) {
            return Set.of();
        }
        List<Long> candidateIds = candidates.stream().map(Meeting::getId).toList();
        Set<Long> joinedMeetingIds = new HashSet<>();
        for (MeetingMemberStatus status :
                List.of(MeetingMemberStatus.PENDING, MeetingMemberStatus.APPROVED)) {
            meetingMemberRepository
                    .findAllByUserIdAndStatusAndMeetingIdInFetchMeeting(
                            userId, status, candidateIds)
                    .forEach(member -> joinedMeetingIds.add(member.getMeeting().getId()));
        }
        return joinedMeetingIds;
    }

    private record ScoredMeeting(Meeting meeting, double score) {}
}
