package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingBookmarkResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingBookmarkService {

    private final MeetingBookmarkRepository meetingBookmarkRepository;
    private final MeetingRepository meetingRepository;

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

    /** 정렬은 항상 북마크한 시각(최신) 순으로 고정한다 — 클라이언트가 보낸 sort는 반영하지 않는다(페이지/사이즈만 사용). */
    @Transactional(readOnly = true)
    public PageResponse<MeetingResponse> getBookmarkedMeetings(
            PostingCategory category, String keyword, Pageable pageable) {
        Long userId = SecurityUtil.getCurrentUserId();
        Pageable unsortedPageable =
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Meeting> meetings =
                meetingBookmarkRepository.findBookmarkedMeetings(
                        userId, category, keyword, unsortedPageable);

        Page<MeetingResponse> responses =
                meetings.map(
                        meeting -> MeetingResponse.from(meeting, resolveDisplayStatus(meeting)));

        return PageResponse.from(responses);
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
