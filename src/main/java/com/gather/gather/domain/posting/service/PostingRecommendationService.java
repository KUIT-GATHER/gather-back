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

    /** 추천 후보 풀 크기. 전량을 메모리에서 채점·정렬하므로 상한을 둔다(카탈로그 규모가 커지면 DB 사전 필터 추가 검토). */
    private static final int CANDIDATE_POOL_SIZE = 200;

    private final PostingRepository postingRepository;
    private final PostingParticipationRepository postingParticipationRepository;
    private final PreferredCategoryResolver preferredCategoryResolver;
    private final RegionNameResolver regionNameResolver;
    private final CategoryDeadlineScoreCalculator scoreCalculator;

    public List<PostingSummaryResponse> getRecommendedPostings() {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        Set<PostingCategory> preferredCategories = preferredCategoryResolver.resolve(userId);
        Set<Long> excludedPostingIds = resolveAppliedPostingIds(userId);

        // 후보가 CANDIDATE_POOL_SIZE를 초과하면 정렬 없이는 임의의(대략 PK순) 일부만 잘려 채점 대상에서
        // 아예 누락될 수 있으므로, 마감일이 가까운 순으로 정렬해 근접도 점수가 높은 후보가 항상 풀에 포함되게 한다.
        Pageable candidatePage =
                PageRequest.of(
                        0, CANDIDATE_POOL_SIZE, Sort.by(Sort.Direction.ASC, "noticeEndDate"));
        Page<Posting> candidates =
                postingRepository.search(
                        PostingStatus.RECRUITING, null, null, null, null, null, candidatePage);

        LocalDateTime now = LocalDateTime.now();
        List<Posting> ranked =
                candidates.getContent().stream()
                        .filter(posting -> !excludedPostingIds.contains(posting.getId()))
                        .map(
                                posting ->
                                        new ScoredPosting(
                                                posting,
                                                scoreOf(posting, preferredCategories, now)))
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

    private double scoreOf(
            Posting posting, Set<PostingCategory> preferredCategories, LocalDateTime now) {
        LocalDateTime deadline =
                posting.getNoticeEndDate() == null
                        ? null
                        : posting.getNoticeEndDate().atTime(LocalTime.MAX);
        return scoreCalculator.score(posting.getCategory(), preferredCategories, deadline, now);
    }

    /** 진행 상태와 무관하게(APPLIED/CONFIRMED/COMPLETED/REVIEWED) 이미 참여 이력이 있는 공고는 추천에서 제외한다. */
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
