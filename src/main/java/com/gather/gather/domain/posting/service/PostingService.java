package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.PostingLocationResponse;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostingService {

    /**
     * {@link PostingRepository#search}가 JPQL로 정렬을 적용하기 때문에, 존재하지 않는 프로퍼티로 정렬을 시도하면 500
     * INTERNAL_SERVER_ERROR로 이어진다(Hibernate가 속성을 못 찾아 던지는 예외를 GlobalExceptionHandler의 catch-all이
     * 받음). 클라이언트 입력값 문제이므로 쿼리 실행 전에 검증해 400으로 응답한다.
     */
    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(
                    "id",
                    "title",
                    "status",
                    "actStartDate",
                    "actEndDate",
                    "noticeStartDate",
                    "noticeEndDate",
                    "recruitCount",
                    "applicantCount",
                    "createdAt",
                    "updatedAt");

    private final PostingRepository postingRepository;
    private final PostingLocationRepository postingLocationRepository;
    private final RegionRepository regionRepository;
    private final PostingSearchLogService postingSearchLogService;
    private final RegionNameResolver regionNameResolver;
    private final BookmarkRepository bookmarkRepository;
    private final PostingParticipationRepository postingParticipationRepository;

    @Transactional(readOnly = true)
    public PageResponse<PostingSummaryResponse> getPostings(
            Pageable pageable,
            Long regionId,
            Long regionGroupId,
            PostingStatus status,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String keyword,
            PostingCategory category) {
        validateSort(pageable.getSort());
        PostingStatus effectiveStatus = status != null ? status : PostingStatus.RECRUITING;
        List<Long> regionIds = resolveRegionIds(regionId, regionGroupId);

        Page<Posting> postings =
                postingRepository.search(
                        effectiveStatus,
                        regionIds,
                        noticeStartDate,
                        noticeEndDate,
                        keyword,
                        category,
                        pageable);

        logSearchKeywordSafely(keyword);

        Map<Long, String> regionNames = regionNameResolver.resolve(postings);

        Page<PostingSummaryResponse> responses =
                postings.map(
                        posting ->
                                PostingSummaryResponse.from(
                                        posting, regionNames.get(posting.getRegionId())));

        return PageResponse.from(responses);
    }

    @Transactional(readOnly = true)
    public PostingResponse getPosting(Long id) {
        Posting posting =
                postingRepository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        String regionName =
                posting.getRegionId() != null
                        ? regionRepository
                                .findById(posting.getRegionId())
                                .map(Region::getName)
                                .orElse(null)
                        : null;
        return PostingResponse.from(
                posting,
                regionName,
                buildLocations(posting),
                isBookmarkedByCurrentUser(id),
                resolveParticipationStatus(id));
    }

    /** 인증이 선택적인 엔드포인트이므로, 로그인하지 않은 사용자는 항상 false를 받는다. */
    private boolean isBookmarkedByCurrentUser(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return userId != null && bookmarkRepository.existsByUserIdAndPostingId(userId, postingId);
    }

    /** 인증이 선택적인 엔드포인트이므로, 로그인하지 않은 사용자는 항상 참여 이력 없음(null)으로 취급한다. */
    private PostingParticipationStatus resolveParticipationStatus(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        if (userId == null) {
            return null;
        }
        return postingParticipationRepository
                .findByUserIdAndPostingId(userId, postingId)
                .map(PostingParticipation::getStatus)
                .orElse(null);
    }

    /**
     * 검색이 성공한 뒤에만 호출한다. {@code postingSearchLogService.log}는 REQUIRES_NEW로 분리된 트랜잭션이라 자체 try/catch로
     * 본문 예외를 흡수하지만, 프록시가 메서드 리턴 후 수행하는 커밋 단계의 실패까지는 막지 못한다. 그 경우에도 검색 응답이 500으로 실패하지 않도록 호출부에서 한 번
     * 더 감싼다.
     */
    private void logSearchKeywordSafely(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        try {
            postingSearchLogService.log(keyword);
        } catch (RuntimeException e) {
            log.warn("검색어 로깅 실패. keyword 길이={}", keyword.length(), e);
        }
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    /** regionId(단일 시도/시군구)와 regionGroupId(9버튼 권역)는 동시에 줄 수 없다 — 필터 기준이 서로 다른 축이라 모호하다. */
    private List<Long> resolveRegionIds(Long regionId, Long regionGroupId) {
        if (regionId != null && regionGroupId != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (regionGroupId != null) {
            return regionRepository.findIdsIncludingChildrenByGroupId(regionGroupId);
        }
        if (regionId != null) {
            return regionRepository.findIdsIncludingChildren(regionId);
        }
        return null;
    }

    private List<PostingLocationResponse> buildLocations(Posting posting) {
        List<PostingLocationResponse> locations = new ArrayList<>();
        locations.add(PostingLocationResponse.first(posting));
        postingLocationRepository
                .findAllByPostingIdOrderByLocationSeq(posting.getId())
                .forEach(location -> locations.add(PostingLocationResponse.from(location)));
        return locations;
    }
}
