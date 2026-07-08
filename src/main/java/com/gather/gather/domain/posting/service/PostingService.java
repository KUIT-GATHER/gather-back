package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.category.repository.CategoryRepository;
import com.gather.gather.domain.posting.dto.PostingLocationResponse;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostingService {

    private final PostingRepository postingRepository;
    private final PostingLocationRepository postingLocationRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PageResponse<PostingSummaryResponse> getPostings(
            Pageable pageable,
            Long regionId,
            PostingStatus status,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String keyword) {
        PostingStatus effectiveStatus = status != null ? status : PostingStatus.RECRUITING;
        List<Long> regionIds =
                regionId != null ? regionRepository.findIdsIncludingChildren(regionId) : null;

        Page<Posting> postings =
                postingRepository.search(
                        effectiveStatus,
                        regionIds,
                        noticeStartDate,
                        noticeEndDate,
                        keyword,
                        pageable);

        Map<Long, String> regionNames = findRegionNames(postings);
        Map<Long, String> categoryNames = findCategoryNames(postings);

        Page<PostingSummaryResponse> responses =
                postings.map(
                        posting ->
                                PostingSummaryResponse.from(
                                        posting,
                                        regionNames.get(posting.getRegionId()),
                                        categoryNames.get(posting.getCategoryId())));

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
        String categoryName =
                categoryRepository
                        .findById(posting.getCategoryId())
                        .map(Category::getName)
                        .orElse(null);

        return PostingResponse.from(posting, regionName, categoryName, buildLocations(posting));
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

    private Map<Long, String> findCategoryNames(Page<Posting> postings) {
        Set<Long> categoryIds =
                postings.getContent().stream()
                        .map(Posting::getCategoryId)
                        .collect(Collectors.toSet());
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }
}
