package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.util.CategoryDeadlineScoreCalculator;
import com.gather.gather.global.util.PreferredCategoryResolver;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingRecommendationService {

    private static final int RECOMMENDATION_COUNT = 5;

    /** 후보를 한 번에 조회할 페이지 크기. */
    private static final int PAGE_SIZE = 200;

    /**
     * 채점 대상 후보를 스캔할 안전 상한. 마감일순으로 페이지를 넘겨가며 전량을 스캔해 진짜 전체 후보 기준 상위 5개를 보장하되, 카탈로그가 비정상적으로 커진 경우 무한정
     * 스캔하지 않도록 상한을 둔다(도달 시 조용히 자르지 않고 경고 로그를 남긴다).
     */
    private static final int MAX_CANDIDATE_SCAN_SIZE = 5000;

    /**
     * {@code MeetingRepository#searchMeetings}는 지역 필터를 끄더라도 empty-IN 방지용 더미값이 필요하다(MeetingService와
     * 동일).
     */
    private static final List<Long> NO_REGION_FILTER = List.of(-1L);

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final PreferredCategoryResolver preferredCategoryResolver;
    private final CategoryDeadlineScoreCalculator scoreCalculator;

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

        return ranked.stream()
                .map(meeting -> MeetingResponse.from(meeting, MeetingStatus.RECRUITING))
                .toList();
    }

    /**
     * 마감일이 가까운 순으로 페이지를 넘겨가며 전체 후보를 채점한다. 첫 페이지만 잘라 채점하면 이후 페이지에 있는 더 높은 점수의 후보(예: 마감일은 멀지만 선호
     * 카테고리인 모임)가 통째로 누락될 수 있어, 안전 상한(MAX_CANDIDATE_SCAN_SIZE)까지 전량을 순회한다.
     */
    private List<ScoredMeeting> scoreAllCandidates(
            Set<PostingCategory> preferredCategories,
            Set<Long> excludedMeetingIds,
            LocalDateTime now) {
        List<ScoredMeeting> scored = new ArrayList<>();
        Sort sort = Sort.by(Sort.Direction.ASC, "deadline");
        int scanned = 0;
        int pageNumber = 0;
        while (scanned < MAX_CANDIDATE_SCAN_SIZE) {
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
            scanned += content.size();
            content.stream()
                    .filter(meeting -> !excludedMeetingIds.contains(meeting.getId()))
                    .forEach(
                            meeting ->
                                    scored.add(
                                            new ScoredMeeting(
                                                    meeting,
                                                    scoreCalculator.score(
                                                            meeting.getCategory(),
                                                            preferredCategories,
                                                            meeting.getDeadline(),
                                                            now))));
            if (!candidates.hasNext()) {
                break;
            }
            pageNumber++;
        }
        if (scanned >= MAX_CANDIDATE_SCAN_SIZE) {
            log.warn("모임 추천 후보 스캔이 안전 상한({}건)에 도달해 이후 후보는 채점에서 제외됐습니다.", MAX_CANDIDATE_SCAN_SIZE);
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

    private record ScoredMeeting(Meeting meeting, double score) {}
}
