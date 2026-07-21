package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingBookmarkResponse;
import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
}
