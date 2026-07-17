package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.PostingLocationResponse;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Map<Long, String> regionNames = findRegionNames(postings);

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
        return PostingResponse.from(posting, regionName, buildLocations(posting));
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

    private Map<Long, String> findRegionNames(Page<Posting> postings) {
        Set<Long> regionIds =
                postings.getContent().stream()
                        .map(Posting::getRegionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return regionRepository.findAllById(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Region::getName));
    }
}
