package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "봉사공고 기반 모임 목록 응답")
public record PostingMeetingResponse(
        @Schema(description = "모임 ID", example = "12") Long meetingId,
        @Schema(description = "모임 이름", example = "한강공원 플로깅팀") String name,
        @Schema(description = "모임 카테고리", example = "[\"ENVIRONMENT\", \"EDUCATION\"]")
                Set<PostingCategory> categories,
        @Schema(description = "현재 모임 인원", example = "12") Integer currentMemberCount,
        @Schema(description = "최대 모임 인원", example = "20") Integer maxMember,
        @Schema(description = "모임 상태", example = "RECRUITING") MeetingStatus status,
        @Schema(description = "현재 사용자의 모임 가입 여부", example = "true") boolean member,
        @Schema(description = "현재 사용자의 모임장 여부", example = "false") boolean host) {

    public static PostingMeetingResponse from(
            Meeting meeting, MeetingStatus displayStatus, boolean member, boolean host) {
        return new PostingMeetingResponse(
                meeting.getId(),
                meeting.getName(),
                Set.copyOf(meeting.getCategories()),
                meeting.getCurrentMemberCount(),
                meeting.getMaxMember(),
                displayStatus,
                member,
                host);
    }
}
