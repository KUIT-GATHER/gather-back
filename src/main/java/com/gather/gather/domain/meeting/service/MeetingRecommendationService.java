package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.service.RegionNameResolver;
import com.gather.gather.global.util.CategoryDeadlineScoreCalculator;
import com.gather.gather.global.util.PreferredCategoryResolver;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /** 후보를 한 번에 조회할 페이지 크기. */
    private static final int PAGE_SIZE = 200;

    /**
     * {@code MeetingRepository#searchMeetings}는 지역 필터를 끄더라도 empty-IN 방지용 더미값이 필요하다(MeetingService와
     * 동일).
     */
    private static final List<Long> NO_REGION_FILTER = List.of(-1L);

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final PreferredCategoryResolver preferredCategoryResolver;
    private final CategoryDeadlineScoreCalculator scoreCalculator;
    private final RegionNameResolver regionNameResolver;

    public List<MeetingResponse> getRecommendedMeetings() {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        Set<PostingCategory> preferredCategories = preferredCategoryResolver.resolve(userId);
        Set<Long> excludedMeetingIds = resolveJoinedMeetingIds(userId);

        LocalDateTime now = LocalDateTime.now();
        List<ScoredMeeting> scored =
                scoreAllCandidates(preferredCategories, excludedMeetingIds, now);

        List<Meeting> ranked =
                scored.stream()
                        .sorted(
                                Comparator.comparingDouble(ScoredMeeting::score)
                                        .reversed()
                                        .thenComparing(sm -> sm.meeting().getDeadline())
                                        .thenComparing(sm -> sm.meeting().getId()))
                        .limit(RECOMMENDATION_COUNT)
                        .map(ScoredMeeting::meeting)
                        .toList();

        Map<Long, String> regionNames =
                regionNameResolver.resolve(ranked.stream().map(Meeting::getRegionId).toList());

        return ranked.stream()
                .map(
                        meeting ->
                                MeetingResponse.from(
                                        meeting,
                                        MeetingStatus.RECRUITING,
                                        regionNames.get(meeting.getRegionId())))
                .toList();
    }

    /**
     * 마감일이 가까운 순으로 페이지를 넘겨가며 전체 후보를 채점한다. 첫 페이지만 잘라 채점하면 이후 페이지에 있는 더 높은 점수의 후보(예: 마감일은 멀지만 선호
     * 카테고리인 모임)가 통째로 누락될 수 있어, 후보가 소진될 때까지 전량을 순회한다.
     */
    private List<ScoredMeeting> scoreAllCandidates(
            Set<PostingCategory> preferredCategories,
            Set<Long> excludedMeetingIds,
            LocalDateTime now) {
        List<ScoredMeeting> scored = new ArrayList<>();
        Sort sort = Sort.by(Sort.Direction.ASC, "deadline");
        int pageNumber = 0;
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE, sort);
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
                            pageable);
            List<Meeting> content = candidates.getContent();
            if (content.isEmpty()) {
                break;
            }
            content.stream()
                    .filter(meeting -> !excludedMeetingIds.contains(meeting.getId()))
                    .forEach(
                            meeting ->
                                    scored.add(
                                            new ScoredMeeting(
                                                    meeting,
                                                    scoreCalculator.score(
                                                            resolveScoringCategory(
                                                                    meeting, preferredCategories),
                                                            preferredCategories,
                                                            meeting.getDeadline(),
                                                            now))));
            if (!candidates.hasNext()) {
                break;
            }
            pageNumber++;
        }
        return scored;
    }

    /**
     * 이미 가입(APPROVED)했거나 가입 신청 중(PENDING)인 모임은 추천에서 제외한다. 후보 페이지와 무관하게 사용자의 전체 가입 이력을 조회해야 후보가 여러
     * 페이지에 걸쳐 있어도 빠짐없이 제외할 수 있다. userId가 인증된 값인데 실제 회원이 없는 경우(탈퇴 직후 등)는 이 메서드 호출 이전에 이미 {@link
     * #getRecommendedMeetings}에서 {@code preferredCategoryResolver.resolve(userId)}가 경고 로그를 남기므로,
     * 여기서 별도로 다시 확인·로깅하지 않는다.
     */
    private Set<Long> resolveJoinedMeetingIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<Long> joinedMeetingIds = new HashSet<>();
        for (MeetingMemberStatus status :
                List.of(MeetingMemberStatus.PENDING, MeetingMemberStatus.APPROVED)) {
            meetingMemberRepository
                    .findAllByUserIdAndStatusFetchMeeting(userId, status)
                    .forEach(member -> joinedMeetingIds.add(member.getMeeting().getId()));
        }
        return joinedMeetingIds;
    }

    /**
     * 모임은 카테고리를 여러 개 가질 수 있으므로, 선호 카테고리와 하나라도 겹치면 그 카테고리를 넘겨 만점을 받도록 하고, 겹치지 않으면 임의의 카테고리를 넘겨도
     * {@link CategoryDeadlineScoreCalculator}가 0점 처리한다.
     */
    private PostingCategory resolveScoringCategory(
            Meeting meeting, Set<PostingCategory> preferredCategories) {
        return meeting.getCategories().stream()
                .filter(preferredCategories::contains)
                .findFirst()
                .orElseGet(() -> meeting.getCategories().iterator().next());
    }

    private record ScoredMeeting(Meeting meeting, double score) {}
}
