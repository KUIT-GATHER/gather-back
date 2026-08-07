package com.gather.gather.domain.posting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.service.MeetingImageUrlResolver;
import com.gather.gather.domain.posting.dto.PostingListItem;
import com.gather.gather.domain.posting.dto.PostingLocationResponse;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSourceType;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository.SearchResult;
import com.gather.gather.domain.posting.repository.UnifiedPostingRow;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostingService {

    private final PostingRepository postingRepository;
    private final PostingLocationRepository postingLocationRepository;
    private final RegionRepository regionRepository;
    private final PostingSearchLogService postingSearchLogService;
    private final RegionNameResolver regionNameResolver;
    private final BookmarkRepository bookmarkRepository;
    private final PostingParticipationRepository postingParticipationRepository;
    private final UnifiedPostingQueryRepository unifiedPostingQueryRepository;
    private final MeetingImageRepository meetingImageRepository;
    private final MeetingImageUrlResolver meetingImageUrlResolver;
    private final ObjectMapper objectMapper;

    /**
     * 앱 전체 봉사공고 목록(#9). 기존 봉사공고와 external=true인 모임 모집공고를 하나의 페이지네이션·정렬 안에서 함께 반환한다.
     *
     * <p>noticeStartDate/noticeEndDate 필터는 기존 봉사공고에만 적용된다(모집공고에는 대응 개념이 없어 항상 포함).
     */
    @Transactional(readOnly = true)
    public PageResponse<PostingListItem> getPostings(
            Pageable pageable,
            Long regionId,
            Long regionGroupId,
            PostingStatus status,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String keyword,
            PostingCategory category) {
        validateSort(pageable.getSort());
        List<Long> regionIds = resolveRegionIds(regionId, regionGroupId);

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        status,
                        regionIds,
                        noticeStartDate,
                        noticeEndDate,
                        keyword,
                        category,
                        pageable);

        logSearchKeywordSafely(keyword);

        List<PostingListItem> items = toListItems(result.rows());
        long totalElements = result.totalElements();
        int totalPages =
                pageable.getPageSize() == 0
                        ? 0
                        : (int) Math.ceil((double) totalElements / pageable.getPageSize());
        return new PageResponse<>(
                items, totalElements, totalPages, pageable.getPageNumber(), pageable.getPageSize());
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
            if (!unifiedPostingQueryRepository.isSortable(order.getProperty())) {
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

    private List<PostingListItem> toListItems(List<UnifiedPostingRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> regionIds = new HashSet<>();
        Set<Long> meetingIds = new HashSet<>();
        for (UnifiedPostingRow row : rows) {
            if (row.regionId() != null) {
                regionIds.add(row.regionId());
            }
            if (row.meetingId() != null) {
                meetingIds.add(row.meetingId());
            }
        }
        Map<Long, String> regionNames = regionNameResolver.resolve(regionIds);
        Map<Long, String> thumbnails = resolveThumbnails(meetingIds);

        return rows.stream()
                .map(
                        row ->
                                toListItem(
                                        row,
                                        regionNames.get(row.regionId()),
                                        row.meetingId() == null
                                                ? null
                                                : thumbnails.get(row.meetingId())))
                .toList();
    }

    /**
     * 모임별 대표 이미지(첫 순번) URL을 배치 조회한다.
     *
     * <p>{@code Map.of()}는 {@code get(null)} 호출 시 예외를 던지므로(불변 맵의 null-key 거부 동작), 일반 봉사공고(POSTING,
     * meetingId 항상 null)만 있는 페이지에서 호출부가 {@code .get(null)}을 하더라도 안전하도록 일반 빈 맵을 반환한다.
     */
    private Map<Long, String> resolveThumbnails(Set<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return new HashMap<>();
        }
        return meetingImageRepository.findRepresentativeImagesByMeetingIds(meetingIds).stream()
                .collect(
                        Collectors.toMap(
                                MeetingImage::getMeetingId,
                                image -> meetingImageUrlResolver.resolve(image.getObjectKey())));
    }

    private PostingListItem toListItem(
            UnifiedPostingRow row, String regionName, String thumbnailUrl) {
        PostingSourceType sourceType = PostingSourceType.valueOf(row.sourceType());
        return new PostingListItem(
                sourceType,
                row.id(),
                row.meetingId(),
                row.title(),
                row.organizationName(),
                sourceType == PostingSourceType.MEETING_RECRUIT ? thumbnailUrl : null,
                row.regionId(),
                regionName,
                row.place(),
                row.activityStartAt(),
                row.activityEndAt(),
                row.applyDeadlineAt(),
                row.maxParticipants(),
                row.appliedCount(),
                parseCategories(row.categoriesJson()),
                row.status());
    }

    private List<PostingCategory> parseCategories(String categoriesJson) {
        if (categoriesJson == null || categoriesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> names =
                    objectMapper.readValue(categoriesJson, new TypeReference<List<String>>() {});
            return names.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(PostingCategory::valueOf)
                    .toList();
        } catch (Exception e) {
            log.warn("모집공고 카테고리 JSON 파싱 실패: {}", categoriesJson, e);
            return Collections.emptyList();
        }
    }
}
