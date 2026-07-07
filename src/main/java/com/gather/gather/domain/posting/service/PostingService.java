package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.category.repository.CategoryRepository;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
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
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PageResponse<PostingSummaryResponse> getPostings(Pageable pageable) {
        Page<Posting> postings = postingRepository.findByStatus(PostingStatus.RECRUITING, pageable);

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
