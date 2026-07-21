package com.gather.gather.domain.meeting.dto;

public record MeetingBookmarkResponse(Long meetingId, boolean bookmarked) {

    public static MeetingBookmarkResponse of(Long meetingId, boolean bookmarked) {
        return new MeetingBookmarkResponse(meetingId, bookmarked);
    }
}
