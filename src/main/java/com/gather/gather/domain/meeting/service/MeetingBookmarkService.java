package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingBookmarkResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.service.RegionNameResolver;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.LikeKeywordEscaper;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingBookmarkService {

    private final MeetingBookmarkRepository meetingBookmarkRepository;
    private final MeetingRepository meetingRepository;
    private final RegionRepository regionRepository;
    private final RegionNameResolver regionNameResolver;

    @Transactional
    public MeetingBookmarkResponse addBookmark(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        if (meetingRepository.findByIdAndDeletedAtIsNull(meetingId).isEmpty()) {
            throw new BusinessException(ErrorCode.MEETING_NOT_FOUND);
        }
        if (meetingBookmarkRepository.existsByUserIdAndMeetingId(userId, meetingId)) {
            throw new BusinessException(ErrorCode.MEETING_BOOKMARK_DUPLICATE);
        }

        try {
            meetingBookmarkRepository.saveAndFlush(MeetingBookmark.create(userId, meetingId));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.MEETING_BOOKMARK_DUPLICATE);
        }
        return MeetingBookmarkResponse.of(meetingId, true);
    }

    @Transactional
    public MeetingBookmarkResponse removeBookmark(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        int deletedCount = meetingBookmarkRepository.deleteByUserIdAndMeetingId(userId, meetingId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.MEETING_BOOKMARK_NOT_FOUND);
        }
        return MeetingBookmarkResponse.of(meetingId, false);
    }

    /**
     * 정렬은 항상 북마크한 시각(최신) 순으로 고정한다 — 개인 북마크 목록에 별도 정렬 옵션을 둘 이유가 없고, 클라이언트 정렬을 그대로 JPQL에 흘려보내면 프로퍼티명이
     * 검증되지 않아 깨진 쿼리로 이어질 수 있다. 그래서 sort를 조용히 무시하는 대신, sort가 지정된 요청은 400으로 명시적으로 거부한다(page/size만
     * 받는다).
     */
    @Transactional(readOnly = true)
    public PageResponse<MeetingResponse> getBookmarkedMeetings(
            PostingCategory category,
            String keyword,
            Long regionId,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            Pageable pageable) {
        rejectSort(pageable.getSort());
        rejectInvertedRange(activityStartDate, activityEndDate);
        Long userId = SecurityUtil.getCurrentUserId();
        Pageable unsortedPageable =
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        // 지역: 상위(시·도) 선택 시 하위 시군구·읍면동까지 포함(모임 목록 조회와 동일 정책).
        boolean hasRegionFilter = regionId != null;
        List<Long> regionIds =
                hasRegionFilter ? regionRepository.findIdsIncludingChildren(regionId) : List.of();
        List<Long> regionIdParam = regionIds.isEmpty() ? List.of(-1L) : regionIds;
        LocalDateTime activityStartAt =
                activityStartDate == null ? null : activityStartDate.atStartOfDay();
        LocalDateTime activityEndAt =
                activityEndDate == null ? null : activityEndDate.atTime(LocalTime.MAX);

        Page<Meeting> meetings =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId,
                        category,
                        LikeKeywordEscaper.escape(keyword),
                        hasRegionFilter,
                        regionIdParam,
                        activityStartAt,
                        activityEndAt,
                        unsortedPageable);
        Map<Long, String> regionNames =
                regionNameResolver.resolve(
                        meetings.getContent().stream().map(Meeting::getRegionId).toList());

        Page<MeetingResponse> responses =
                meetings.map(
                        meeting ->
                                MeetingResponse.from(
                                        meeting,
                                        resolveDisplayStatus(meeting),
                                        regionNames.get(meeting.getRegionId())));

        return PageResponse.from(responses);
    }

    /** 시작일·종료일이 모두 주어졌는데 시작일이 종료일보다 늦으면(역전 범위) 빈 목록 대신 명시적으로 400을 반환한다. */
    private void rejectInvertedRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void rejectSort(Sort sort) {
        if (sort.isSorted()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * 표시용 모집 상태.
     *
     * <p>{@code MeetingService.resolveDisplayStatus}와 동일한 규칙을 북마크 목록용으로 복제한 것(이미 {@code
     * MeetingHomeService}에도 같은 방식으로 복제되어 있음). 공용 유틸 추출은 다른 도메인 소유자(연석/현승)와의 협의가 필요해 이 PR 범위에서는 다루지
     * 않는다.
     */
    private MeetingStatus resolveDisplayStatus(Meeting meeting) {
        LocalDateTime now = LocalDateTime.now();

        if (meeting.getStatus() == MeetingStatus.COMPLETED || meeting.isActivityEnded(now)) {
            return MeetingStatus.COMPLETED;
        }
        if (meeting.getStatus() == MeetingStatus.CLOSED
                || meeting.isDeadlinePassed(now)
                || meeting.isFull()) {
            return MeetingStatus.CLOSED;
        }
        return MeetingStatus.RECRUITING;
    }
}
