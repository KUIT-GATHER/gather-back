package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinRequestResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinResponse;
import com.gather.gather.domain.meeting.dto.MeetingRecognizedMinutesRequest;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.dto.MeetingUpdateRequest;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.service.MeetingKeywordRecommendationService;
import com.gather.gather.domain.meeting.service.MeetingRecommendationService;
import com.gather.gather.domain.meeting.service.MeetingService;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting", description = "모임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingKeywordRecommendationService meetingKeywordRecommendationService;
    private final MeetingRecommendationService meetingRecommendationService;

    @Operation(
            summary = "모임 생성",
            description =
                    "로그인한 사용자가 새로운 모임을 생성합니다. "
                            + "자유 모임은 categories에 카테고리를 1개 이상 3개 이하로 전달합니다. "
                            + "자유 모임은 volunteerPostingId, activityStartAt, activityEndAt을 생략하거나 null로 요청할 수 있습니다. "
                            + "공고 기반 모임은 volunteerPostingId와 활동 시작·종료 시간이 모두 필요합니다. "
                            + "활동 기간을 전달하는 경우 활동 시작 시간은 종료 시간보다 빨라야 하며, "
                            + "신청 마감 시간은 활동 시작 시간보다 늦을 수 없습니다. "
                            + "최대 인원은 자유 모임·공고 기반 모임 모두 30명까지 설정할 수 있습니다(MEETING_MAX_MEMBER_EXCEEDED). "
                            + "timeRecognized(봉사시간 인정 여부)는 공고 기반 모임에서만 반영되며, 자유 모임은 항상 false로 저장됩니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeetingResponse> createMeeting(
            @Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.success(meetingService.createMeeting(request));
    }

    @Operation(
            summary = "모임 목록 조회",
            description =
                    "모임을 페이지 단위로 조회합니다. 인증이 필요 없습니다. "
                            + "regionId는 상위 지역(시/도) 선택 시 하위 시군구 모임까지 포함합니다. "
                            + "activityStartDate/activityEndDate는 선택 기간과 모임 활동기간(activityStartAt~activityEndAt)이 "
                            + "겹치는 모임을 조회하며, 활동 기간이 정해지지 않은 자유 모임은 날짜 필터 사용 시 제외됩니다. "
                            + "status=RECRUITING이면 마감 전·정원 미달이며 활동이 종료되지 않은 실제 가입 가능한 모임만 반환합니다. "
                            + "활동 종료 시간이 없는 자유 모임도 신청 마감 전이고 정원이 남아 있으면 포함됩니다. "
                            + "postingBasedFirst=true면 공고 기반 모임을 먼저 배치하고 "
                            + "자유 모임을 뒤에 두며, 그룹 내부 정렬은 sort를 따릅니다(기본 createdAt,desc). "
                            + "다중 카테고리에는 단일 정렬 기준이 없어 category 정렬은 지원하지 않으며, 요청 시 400을 반환합니다.",
            parameters = {
                @Parameter(
                        name = "sort",
                        description =
                                "정렬 기준 (property,direction). 예: createdAt,desc(최신순), "
                                        + "currentMemberCount,desc(인기순), deadline,asc(마감임박). "
                                        + "허용 필드: id, name, currentMemberCount, maxMember, regionId, "
                                        + "status, deadline, activityStartAt, activityEndAt, "
                                        + "createdAt, updatedAt.",
                        example = "createdAt,desc")
            })
    @GetMapping
    public ApiResponse<PageResponse<MeetingResponse>> getMeetings(
            @RequestParam(required = false) String keyword,
            @Parameter(description = "지역 ID (상위 시/도 선택 시 하위 시군구 모임 포함)")
                    @RequestParam(required = false)
                    Long regionId,
            @Parameter(description = "해당 카테고리를 하나라도 포함한 모임 조회", example = "WELFARE")
                    @RequestParam(required = false)
                    PostingCategory category,
            @Parameter(description = "모집 상태. RECRUITING이면 실제 가입 가능한 모임만 반환")
                    @RequestParam(required = false)
                    MeetingStatus status,
            @Parameter(description = "활동 기간 시작일 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate activityStartDate,
            @Parameter(description = "활동 기간 종료일 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate activityEndDate,
            @Parameter(description = "true면 공고 기반 모임을 우선 정렬", example = "true")
                    @RequestParam(required = false)
                    Boolean postingBasedFirst,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(
                meetingService.getMeetings(
                        keyword,
                        regionId,
                        category,
                        status,
                        activityStartDate,
                        activityEndDate,
                        postingBasedFirst,
                        pageable));
    }

    @Operation(
            summary = "모임 추천 목록 조회",
            description =
                    "선호 카테고리 매칭과 마감일 근접도로 점수를 매겨 상위 5개 모임을 추천합니다. 인증이 필요 없으며, "
                            + "비로그인이거나 선호 카테고리를 설정하지 않았으면 마감임박순 상위 5개를 반환합니다. "
                            + "이미 가입했거나 가입 신청 중인 모임은 추천에서 제외됩니다.")
    @GetMapping("/recommended")
    public ApiResponse<List<MeetingResponse>> getRecommendedMeetings() {
        return ApiResponse.success(meetingRecommendationService.getRecommendedMeetings());
    }

    @Operation(
            summary = "모임 추천검색어 목록 조회",
            description =
                    "최근 60일간 모임 검색어를 형태소 분석해 집계한 인기 검색어 상위 10개를 반환합니다. "
                            + "매일 새벽 5시 배치로 갱신되며, 실시간 반영은 아닙니다. 인증이 필요 없습니다.")
    @GetMapping("/keywords/recommended")
    public ApiResponse<List<String>> getRecommendedKeywords() {
        return ApiResponse.success(meetingKeywordRecommendationService.getRecommendedKeywords());
    }

    @Operation(summary = "내 모임 조회", description = "로그인한 사용자가 참여한 모임 목록을 조회합니다.")
    @GetMapping("/my")
    public ApiResponse<List<MeetingResponse>> getMyMeetings() {
        return ApiResponse.success(meetingService.getMyMeetings());
    }

    @Operation(summary = "모임 가입 신청", description = "로그인한 사용자가 특정 모임에 가입을 신청합니다.")
    @PostMapping("/{meetingId}/join")
    public ApiResponse<MeetingJoinResponse> joinMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.joinMeeting(meetingId));
    }

    @Operation(
            summary = "가입 신청 취소",
            description =
                    "신청자 본인이 대기 중인(PENDING) 가입 신청을 취소합니다. 모임장의 거절(REJECTED)과는 별도로 CANCELLED 상태가 됩니다.")
    @DeleteMapping("/{meetingId}/join")
    public ApiResponse<Void> cancelMyJoinRequest(@PathVariable Long meetingId) {
        meetingService.cancelMyJoinRequest(meetingId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "가입 신청 목록 조회", description = "모임장이 승인 대기 중인 가입 신청 목록을 조회합니다.")
    @GetMapping("/{meetingId}/join-requests")
    public ApiResponse<List<MeetingJoinRequestResponse>> getPendingJoinRequests(
            @PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.getPendingJoinRequests(meetingId));
    }

    @Operation(summary = "모임 가입 승인", description = "모임장이 가입 신청을 승인합니다.")
    @PatchMapping("/{meetingId}/join-requests/{joinRequestId}/approve")
    public ApiResponse<MeetingJoinRequestResponse> approveJoinRequest(
            @PathVariable Long meetingId, @PathVariable Long joinRequestId) {
        return ApiResponse.success(meetingService.approveJoinRequest(meetingId, joinRequestId));
    }

    @Operation(summary = "모임 가입 거절", description = "모임장이 가입 신청을 거절합니다.")
    @PatchMapping("/{meetingId}/join-requests/{joinRequestId}/reject")
    public ApiResponse<MeetingJoinRequestResponse> rejectJoinRequest(
            @PathVariable Long meetingId, @PathVariable Long joinRequestId) {
        return ApiResponse.success(meetingService.rejectJoinRequest(meetingId, joinRequestId));
    }

    @Operation(summary = "모임 상세 조회", description = "meetingId에 해당하는 모임 상세 정보를 조회합니다. 인증이 필요 없습니다.")
    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> getMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.getMeeting(meetingId));
    }

    @Operation(
            summary = "모임 해산",
            description =
                    "모임장이 모임을 해산합니다. 해산된 모임은 목록·상세 조회, 게시글, 멤버 정보 등 사용자 화면의 모든 조회에서 제외됩니다."
                            + " 되돌릴 수 없습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "해산 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "모임장이 아님(MEETING_HOST_ONLY)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임을 찾을 수 없음(MEETING_NOT_FOUND)")
    })
    @DeleteMapping("/{meetingId}")
    public ApiResponse<Void> disbandMeeting(@PathVariable Long meetingId) {
        meetingService.disbandMeeting(meetingId);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "모임 정보 수정",
            description =
                    "모임장이 모임 기본 정보(이름, 소개, 최대 인원, 신청 마감일, 카테고리, 참여 조건)를 수정합니다. "
                            + "최대 인원은 현재 참여 인원보다 적게 설정할 수 없으며, "
                            + "자유 모임·공고 기반 모임 모두 최대 30명까지 설정할 수 있습니다. "
                            + "자유 모임은 categories(1~3개)와 regionId를 함께 수정할 수 있습니다. "
                            + "공고 기반 모임은 연결된 봉사공고를 기준으로 지역·카테고리가 고정되어 있어, "
                            + "요청에 포함하더라도 반영되지 않고 기존 값이 유지됩니다. "
                            + "timeRecognized(봉사시간 인정 여부)는 공고 기반 모임에서만 반영되며, "
                            + "자유 모임은 요청 값과 무관하게 항상 false로 유지됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "모임장이 아님(MEETING_HOST_ONLY)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description =
                        "모임 유형별 최대 인원 초과(MEETING_MAX_MEMBER_EXCEEDED) / 요청 값 오류(VALIDATION_ERROR)"
                                + " / 모임 시간 오류(INVALID_MEETING_TIME)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "현재 참여 인원보다 정원이 적음(MEETING_MAX_BELOW_CURRENT_MEMBER)")
    })
    @PatchMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> updateMeeting(
            @PathVariable Long meetingId, @Valid @RequestBody MeetingUpdateRequest request) {
        return ApiResponse.success(meetingService.updateMeeting(meetingId, request));
    }

    @Operation(
            summary = "모임(그룹) 봉사 완료 처리",
            description = "모임장이 모임을 완료 처리한다. 개인 봉사는 본인이 활동종료일 이후 별도 API로 완료 처리한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "완료 처리 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "모임장이 아님(MEETING_HOST_ONLY)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description =
                        "이미 완료됨(MEETING_ALREADY_COMPLETED) / 활동종료일 미경과(MEETING_COMPLETE_NOT_ALLOWED)")
    })
    @PatchMapping("/{meetingId}/complete")
    public ApiResponse<Void> completeMeeting(@PathVariable Long meetingId) {
        meetingService.completeMeeting(meetingId);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "봉사 인정시간 입력",
            description = "완료 처리된 모임에 한해, 승인된 멤버 본인이 직접 인정시간(분 단위, 10분 단위)을 입력한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "인정시간 입력 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "인정시간 형식 오류(10분 단위·양수·상한 이내가 아님, VALIDATION_ERROR)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "승인된 멤버가 아님(MEETING_MEMBER_REQUIRED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description =
                        "완료 처리되지 않음(MEETING_HOURS_NOT_ALLOWED) / 이미 입력됨(MEETING_HOURS_ALREADY_SUBMITTED)")
    })
    @PatchMapping("/{meetingId}/members/me/hours")
    public ApiResponse<Void> submitMemberHours(
            @PathVariable Long meetingId, @RequestBody MeetingRecognizedMinutesRequest request) {
        meetingService.submitMemberHours(meetingId, request.recognizedMinutes());
        return ApiResponse.success(null);
    }
}
