package com.gather.gather.domain.posting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.service.MeetingImageUrlResolver;
import com.gather.gather.domain.posting.dto.PostingListItem;
import com.gather.gather.domain.posting.dto.PostingLocationResponse;
import com.gather.gather.domain.posting.dto.PostingMapItem;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSourceType;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingMapRow;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository.CursorSearchResult;
import com.gather.gather.domain.posting.repository.UnifiedPostingRow;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.CursorPageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostingService {

    /** 클라이언트가 size를 지정하지 않았을 때(0 이하 포함) 쓰는 기본 페이지 크기. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 한 번에 가져올 수 있는 최대 페이지 크기. 과도한 size 요청으로 UNION 쿼리 비용이 커지는 것을 막는다. */
    private static final int MAX_PAGE_SIZE = 100;

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
    private final PostingApplicationUrlResolver postingApplicationUrlResolver;

    /**
     * 앱 전체 봉사공고 목록(#9, 무한스크롤). 기존 봉사공고와 external=true인 모임 모집공고를 하나의 정렬 안에서 함께 반환한다.
     *
     * <p>키셋(커서) 페이지네이션을 쓴다 — {@code cursor}가 null/blank면 첫 페이지를, 값이 있으면 이전 응답의 {@code nextCursor}를
     * 그대로 넘겨 그 다음 페이지를 조회한다. 총 개수(totalElements)는 내려주지 않는다(무한스크롤에 불필요하고, 계산하려면 별도 COUNT 쿼리가 필요해 키셋
     * 페이지네이션의 성능 이점이 사라진다).
     *
     * <p>커서는 조회 당시의 {@code sort}(및 우선순위 버킷에 영향을 주는 {@code status})에 종속적이다. 스크롤 세션 도중 sort를 바꾸면서 이전
     * 커서를 재사용하면 {@link ErrorCode#VALIDATION_ERROR}가 발생한다 — 클라이언트는 정렬을 바꿀 때 커서 없이 처음부터 다시 조회해야 한다.
     *
     * <p>noticeStartDate/noticeEndDate 필터는 기존 봉사공고에만 적용된다(모집공고에는 대응 개념이 없어 항상 포함).
     *
     * <p>activityStartDate/activityEndDate는 두 출처 모두에 적용되는 활동일 겹침(overlap) 필터다. 선택 기간과 실제 활동기간이 하루라도
     * 겹치면 조회된다(활동종료일 &gt;= activityStartDate AND 활동시작일 &lt;= activityEndDate). POSTING은
     * actStartDate/actEndDate 기준이며 값이 없으면 activityDate로 대체하고, MEETING_RECRUIT는
     * activityStartAt/activityEndAt 기준이다.
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<PostingListItem> getPostings(
            Sort sort,
            String cursor,
            int size,
            Long regionId,
            Long regionGroupId,
            PostingStatus status,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            String keyword,
            PostingCategory category) {
        validateSort(sort);
        validateActivityDateRange(activityStartDate, activityEndDate);
        List<Long> regionIds = resolveRegionIds(regionId, regionGroupId);
        int pageSize = resolvePageSize(size);

        CursorSearchResult result =
                unifiedPostingQueryRepository.search(
                        status,
                        regionIds,
                        noticeStartDate,
                        noticeEndDate,
                        activityStartDate,
                        activityEndDate,
                        keyword,
                        category,
                        sort,
                        cursor,
                        pageSize);

        logSearchKeywordSafely(keyword);

        List<PostingListItem> items = toListItems(result.rows());
        return new CursorPageResponse<>(items, result.nextCursor(), result.hasNext());
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

        PostingParticipation participation = findCurrentUserParticipation(id).orElse(null);

        return PostingResponse.from(
                posting,
                regionName,
                buildLocations(posting),
                isBookmarkedByCurrentUser(id),
                participation != null ? participation.getStatus() : null,
                postingApplicationUrlResolver.resolve(posting),
                participation != null ? participation.getParticipationStartDate() : null,
                participation != null ? participation.getParticipationEndDate() : null);
    }

    /**
     * 봉사공고 지도 조회(#186). 정책상 일반 봉사공고(POSTING)만 노출하고 모임 모집공고(MEETING_RECRUIT)는 제외한다. 페이지네이션 없이 현재 지도
     * bounds(swLat/swLng ~ neLat/neLng) 안에 활동장소가 있는 공고 전체를 반환한다. bounds는 1번째 장소(Posting 자신의 위·경도)
     * 또는 2·3번째 장소(PostingLocation) 중 하나라도 포함되면 매칭되고, 응답의 locations 배열에는 bounds 여부와 무관하게 해당 공고의
     * 유효한(위·경도가 있는) 장소를 모두 담는다.
     */
    @Transactional(readOnly = true)
    public List<PostingMapItem> getPostingsMap(
            Long regionId,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            PostingCategory category,
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng) {
        validateActivityDateRange(activityStartDate, activityEndDate);
        validateBounds(swLat, swLng, neLat, neLng);
        List<Long> regionIds =
                regionId != null ? regionRepository.findIdsIncludingChildren(regionId) : null;

        List<PostingMapRow> rows =
                postingRepository.searchForMap(
                        regionIds,
                        activityStartDate,
                        activityEndDate,
                        category,
                        swLat,
                        swLng,
                        neLat,
                        neLng);
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> postingIds = rows.stream().map(PostingMapRow::id).collect(Collectors.toSet());
        Set<Long> regionIdsForNames =
                rows.stream()
                        .map(PostingMapRow::regionId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, String> regionNames = regionNameResolver.resolve(regionIdsForNames);
        Map<Long, List<PostingLocationResponse>> extraLocationsByPostingId =
                postingLocationRepository
                        .findAllByPostingIdInOrderByPostingIdAscLocationSeqAsc(postingIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        com.gather.gather.domain.posting.entity.PostingLocation
                                                ::getPostingId,
                                        Collectors.mapping(
                                                PostingLocationResponse::from,
                                                Collectors.toList())));

        return rows.stream()
                .map(
                        row ->
                                toMapItem(
                                        row,
                                        regionNames.get(row.regionId()),
                                        extraLocationsByPostingId.getOrDefault(
                                                row.id(), List.of())))
                .toList();
    }

    private PostingMapItem toMapItem(
            PostingMapRow row, String regionName, List<PostingLocationResponse> extraLocations) {
        List<PostingLocationResponse> locations = new ArrayList<>();
        if (row.latitude() != null && row.longitude() != null) {
            locations.add(
                    new PostingLocationResponse(
                            1, row.postAddress(), row.latitude(), row.longitude()));
        }
        // 2·3번째 장소는 위·경도가 nullable이라(DB 제약 없음), 값이 없는 장소는 지도 응답에서 제외한다
        // (Javadoc/Swagger 계약: locations에는 위·경도가 있는 장소만 담는다).
        extraLocations.stream()
                .filter(location -> location.latitude() != null && location.longitude() != null)
                .forEach(locations::add);

        LocalDate effectiveStart =
                row.actStartDate() != null ? row.actStartDate() : row.activityDate();
        LocalDate effectiveEnd = row.actEndDate() != null ? row.actEndDate() : effectiveStart;

        return new PostingMapItem(
                row.id(),
                row.title(),
                row.recruitOrg(),
                row.regionId(),
                regionName,
                effectiveStart != null ? effectiveStart.atStartOfDay() : null,
                effectiveEnd != null ? effectiveEnd.atStartOfDay() : null,
                row.noticeEndDate() != null ? row.noticeEndDate().atStartOfDay() : null,
                row.category(),
                row.status(),
                locations);
    }

    /** 인증이 선택적인 엔드포인트이므로, 로그인하지 않은 사용자는 항상 false를 받는다. */
    private boolean isBookmarkedByCurrentUser(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return userId != null && bookmarkRepository.existsByUserIdAndPostingId(userId, postingId);
    }

    /** 인증이 선택적인 엔드포인트이므로, 로그인하지 않은 사용자는 항상 참여 이력 없음(null)으로 취급한다. */
    private Optional<PostingParticipation> findCurrentUserParticipation(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        if (userId == null) {
            return Optional.empty();
        }
        return postingParticipationRepository.findByUserIdAndPostingId(userId, postingId);
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

    /** size 미지정(0 이하)이면 기본값을, 상한을 넘으면 최대값을 쓴다 — 잘못된 size로 400을 내지 않고 안전하게 보정한다. */
    private int resolvePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 활동일 필터 시작일이 종료일보다 늦으면 잘못된 요청이다(둘 다 지정된 경우에만 검증). 목록·지도 조회 둘 다 사용한다. */
    private void validateActivityDateRange(LocalDate activityStartDate, LocalDate activityEndDate) {
        if (activityStartDate != null
                && activityEndDate != null
                && activityStartDate.isAfter(activityEndDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * 지도 bounds 4개 값 모두 위도(-90~90)·경도(-180~180) 유효 범위 안에 있어야 하고, 남서쪽이 북동쪽보다 작거나 같아야 한다. 값이 뒤바뀌거나
     * 범위를 벗어나면 조건이 항상 거짓이 되어 "해당 지역에 공고 없음"과 구분되지 않는 빈 결과가 나오므로, 여기서 명시적으로 400을 반환한다.
     */
    private void validateBounds(
            BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {
        if (!isValidLatitude(swLat) || !isValidLatitude(neLat)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!isValidLongitude(swLng) || !isValidLongitude(neLng)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (swLat.compareTo(neLat) > 0 || swLng.compareTo(neLng) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean isValidLatitude(BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(-90)) >= 0
                && value.compareTo(BigDecimal.valueOf(90)) <= 0;
    }

    private boolean isValidLongitude(BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(-180)) >= 0
                && value.compareTo(BigDecimal.valueOf(180)) <= 0;
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
