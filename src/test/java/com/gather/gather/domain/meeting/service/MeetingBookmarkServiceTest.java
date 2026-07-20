package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.meeting.dto.MeetingBookmarkResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MeetingBookmarkServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;

    @Mock private MeetingBookmarkRepository meetingBookmarkRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private Meeting meeting;

    private MeetingBookmarkService meetingBookmarkService;

    @BeforeEach
    void setUp() {
        meetingBookmarkService =
                new MeetingBookmarkService(meetingBookmarkRepository, meetingRepository);
    }

    @Test
    @DisplayName(
            "addBookmark saves a bookmark when the meeting exists and is not already bookmarked")
    void addBookmark_savesBookmark_whenMeetingExistsAndNotDuplicate() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.of(meeting));
            when(meetingBookmarkRepository.existsByUserIdAndMeetingId(USER_ID, MEETING_ID))
                    .thenReturn(false);

            MeetingBookmarkResponse response = meetingBookmarkService.addBookmark(MEETING_ID);

            assertThat(response.meetingId()).isEqualTo(MEETING_ID);
            assertThat(response.bookmarked()).isTrue();
            verify(meetingBookmarkRepository).saveAndFlush(any(MeetingBookmark.class));
        }
    }

    @Test
    @DisplayName("addBookmark throws MEETING_NOT_FOUND when the meeting does not exist")
    void addBookmark_throwsMeetingNotFound_whenMeetingMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingBookmarkService.addBookmark(MEETING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_NOT_FOUND);

            verify(meetingBookmarkRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("addBookmark throws MEETING_BOOKMARK_DUPLICATE when already bookmarked")
    void addBookmark_throwsMeetingBookmarkDuplicate_whenAlreadyBookmarked() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.of(meeting));
            when(meetingBookmarkRepository.existsByUserIdAndMeetingId(USER_ID, MEETING_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> meetingBookmarkService.addBookmark(MEETING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_BOOKMARK_DUPLICATE);

            verify(meetingBookmarkRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName(
            "addBookmark throws MEETING_BOOKMARK_DUPLICATE when a concurrent request wins the"
                    + " unique constraint race")
    void addBookmark_throwsMeetingBookmarkDuplicate_whenConcurrentInsertViolatesUniqueConstraint() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.of(meeting));
            when(meetingBookmarkRepository.existsByUserIdAndMeetingId(USER_ID, MEETING_ID))
                    .thenReturn(false);
            when(meetingBookmarkRepository.saveAndFlush(any(MeetingBookmark.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate entry"));

            assertThatThrownBy(() -> meetingBookmarkService.addBookmark(MEETING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_BOOKMARK_DUPLICATE);
        }
    }

    @Test
    @DisplayName("removeBookmark deletes the bookmark when it exists")
    void removeBookmark_deletesBookmark_whenExists() {
        MeetingBookmark bookmark = MeetingBookmark.create(USER_ID, MEETING_ID);
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingBookmarkRepository.findByUserIdAndMeetingId(USER_ID, MEETING_ID))
                    .thenReturn(Optional.of(bookmark));

            MeetingBookmarkResponse response = meetingBookmarkService.removeBookmark(MEETING_ID);

            assertThat(response.meetingId()).isEqualTo(MEETING_ID);
            assertThat(response.bookmarked()).isFalse();
            verify(meetingBookmarkRepository).delete(bookmark);
        }
    }

    @Test
    @DisplayName("removeBookmark throws MEETING_BOOKMARK_NOT_FOUND when no bookmark exists")
    void removeBookmark_throwsMeetingBookmarkNotFound_whenMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingBookmarkRepository.findByUserIdAndMeetingId(USER_ID, MEETING_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingBookmarkService.removeBookmark(MEETING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_BOOKMARK_NOT_FOUND);

            verify(meetingBookmarkRepository, never()).delete(any());
        }
    }
}
