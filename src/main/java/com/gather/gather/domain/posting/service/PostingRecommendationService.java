package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.util.CategoryDeadlineScoreCalculator;
import com.gather.gather.global.util.PreferredCategoryResolver;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 봉사공고 추천(회원가입 시 선택한 선호 카테고리 + 마감일 근접도 기준 상위 5개).
 *
 * <p>목록 조회/상세 조회를 담당하는 {@link PostingService}와 분리해, 추천 전용 조회·채점·정렬만 담당한다. 비로그인이거나 선호 카테고리가 없는 사용자는
 * 카테고리 점수가 항상 0이 되어 자연히 마감임박순으로 정렬된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostingRecommendationService {

    private static final int RECOMMENDATION_COUNT = 5;

    /** 후보를 한 번에 조회할 페이지 크기. */
    private static final int PAGE_SIZE = 200;

    private final PostingRepository postingRepository;
    private final PostingParticipationRepository postingParticipationRepository;
    private final PreferredCategoryResolver preferredCategoryResolver;
    private final RegionNameResolver regionNameResolver;
    private final CategoryDeadlineScoreCalculator scoreCalculator;

    public List<PostingSummaryResponse> getRecommendedPostings() {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        Set<PostingCategory> preferredCategories = preferredCategoryResolver.resolve(userId);
        Set<Long> excludedPostingIds = resolveAppliedPostingIds(userId);

        LocalDateTime now = LocalDateTime.now();
        List<ScoredPosting> scored =
                scoreAllCandidates(preferredCategories, excludedPostingIds, now);

        List<Posting> ranked =
                scored.stream()
                        .sorted(
                                Comparator.comparingDouble(ScoredPosting::score)
                                        .reversed()
                                        .thenComparing(
                                                sp -> sp.posting().getNoticeEndDate(),
                                                Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparing(sp -> sp.posting().getId()))
                        .limit(RECOMMENDATION_COUNT)
                        .map(ScoredPosting::posting)
                        .toList();

        Map<Long, String> regionNames = regionNameResolver.resolve(new PageImpl<>(ranked));
        return ranked.stream()
                .map(
                        posting ->
                                PostingSummaryResponse.from(
                                        posting, regionNames.get(posting.getRegionId())))
                .toList();
    }

    /**
     * 마감일이 가까운 순으로 페이지를 넘겨가며 전체 후보를 채점한다. 첫 페이지만 잘라 채점하면 이후 페이지에 있는 더 높은 점수의 후보(예: 마감일은 멀지만 선호
     * 카테고리인 공고)가 통째로 누락될 수 있어, 후보가 소진될 때까지 전량을 순회한다. 신청 이력이 있는 공고와 비활성(isActive=false) 공고는 신청 시점에
     * 이미 막혀 있으므로 여기서 미리 제외한다.
     */
    private List<ScoredPosting> scoreAllCandidates(
            Set<PostingCategory> preferredCategories,
            Set<Long> excludedPostingIds,
            LocalDateTime now) {
        List<ScoredPosting> scored = new ArrayList<>();
        Sort sort = Sort.by(Sort.Direction.ASC, "noticeEndDate");
        int pageNumber = 0;
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE, sort);
            Page<Posting> candidates =
                    postingRepository.search(
                            PostingStatus.RECRUITING, null, null, null, null, null, pageable);
            List<Posting> content = candidates.getContent();
            if (content.isEmpty()) {
                break;
            }
            content.stream()
                    .filter(posting -> Boolean.TRUE.equals(posting.getIsActive()))
                    .filter(posting -> !excludedPostingIds.contains(posting.getId()))
                    .forEach(
                            posting ->
                                    scored.add(
                                            new ScoredPosting(
                                                    posting,
                                                    scoreOf(posting, preferredCategories, now))));
            if (!candidates.hasNext()) {
                break;
            }
            pageNumber++;
        }
        return scored;
    }

    private double scoreOf(
            Posting posting, Set<PostingCategory> preferredCategories, LocalDateTime now) {
        LocalDateTime deadline =
                posting.getNoticeEndDate() == null
                        ? null
                        : posting.getNoticeEndDate().atTime(LocalTime.MAX);
        return scoreCalculator.score(posting.getCategory(), preferredCategories, deadline, now);
    }

    /**
     * 진행 상태와 무관하게(APPLIED/CONFIRMED/COMPLETED/REVIEWED) 이미 참여 이력이 있는 공고는 추천에서 제외한다. userId가 인증된 값인데
     * 실제 회원이 없는 경우(탈퇴 직후 등)는 이 메서드 호출 이전에 이미 {@link #getRecommendedPostings}에서 {@code
     * preferredCategoryResolver.resolve(userId)}가 경고 로그를 남기므로, 여기서 별도로 다시 확인·로깅하지 않는다.
     */
    private Set<Long> resolveAppliedPostingIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return postingParticipationRepository.findByUserId(userId).stream()
                .map(PostingParticipation::getPostingId)
                .collect(Collectors.toSet());
    }

    private record ScoredPosting(Posting posting, double score) {}
}
