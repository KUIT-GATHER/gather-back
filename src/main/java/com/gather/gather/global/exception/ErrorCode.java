package com.gather.gather.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    EXPIRED_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    EMAIL_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "이메일 인증 요청을 찾을 수 없습니다."),
    EMAIL_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증 코드를 방금 발송했습니다. 잠시 후 다시 시도해주세요."),
    EMAIL_SEND_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, "하루 이메일 인증 발송 횟수를 초과했습니다. 내일 다시 시도해주세요."),
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, "인증 코드 입력 가능 횟수를 초과했습니다. 코드를 다시 발송해주세요."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관 동의가 필요합니다."),
    INVALID_ACTIVITY_REGION(HttpStatus.BAD_REQUEST, "활동 지역은 시군구 단위로 1개 선택해야 합니다."),
    INVALID_INTEREST_CATEGORY_COUNT(HttpStatus.BAD_REQUEST, "관심 카테고리는 중복 없이 1개 이상 선택해야 합니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "활동 지역을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "관심 카테고리를 찾을 수 없습니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    SUSPENDED_USER(HttpStatus.FORBIDDEN, "이용 정지된 계정입니다."),
    WITHDRAWAL_PENDING_USER(HttpStatus.FORBIDDEN, "탈퇴 처리 중인 계정입니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다."),
    ACCOUNT_REJOIN_BLOCKED(HttpStatus.CONFLICT, "탈퇴 후 7일 동안 재가입할 수 없습니다."),
    ACCOUNT_TERMINATION_STATE_CONFLICT(HttpStatus.CONFLICT, "계정 탈퇴 상태가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REVOKED_TOKEN(HttpStatus.UNAUTHORIZED, "폐기된 토큰입니다."),
    // 소셜 가입용 임시 토큰 전용. 위 access/refresh 토큰 코드와 구분하기 위해 SIGNUP_TOKEN_ 접두사를 쓴다.
    SIGNUP_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "가입 인증이 만료되었습니다. 카카오 로그인부터 다시 진행해주세요."),
    SIGNUP_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 가입 인증입니다. 카카오 로그인부터 다시 진행해주세요."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 계정입니다."),
    SOCIAL_ACCOUNT_NOT_LINKED(HttpStatus.CONFLICT, "연결 해제된 소셜 계정입니다."),
    KAKAO_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인 서비스를 일시적으로 사용할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    MEETING_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 모임입니다."),
    MEETING_JOIN_REQUEST_DUPLICATE(HttpStatus.CONFLICT, "이미 가입을 신청한 모임입니다."),
    MEETING_JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "가입 신청을 찾을 수 없습니다."),
    MEETING_HOST_ONLY(HttpStatus.FORBIDDEN, "모임장만 가입 신청을 처리할 수 있습니다."),
    MEETING_FULL(HttpStatus.CONFLICT, "모임 인원이 가득 찼습니다."),
    MEETING_CLOSED(HttpStatus.CONFLICT, "마감된 모임입니다."),
    INVALID_MEETING_TIME(HttpStatus.BAD_REQUEST, "모임 시간이 올바르지 않습니다."),
    MEETING_BOOKMARK_DUPLICATE(HttpStatus.CONFLICT, "이미 북마크한 모임입니다."),
    MEETING_BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다."),
    MEETING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료 처리된 모임입니다."),
    MEETING_COMPLETE_NOT_ALLOWED(HttpStatus.CONFLICT, "활동종료일이 지나야 완료 처리를 할 수 있습니다."),
    MEETING_HOURS_NOT_ALLOWED(HttpStatus.CONFLICT, "완료 처리된 모임에서만 인정시간을 입력할 수 있습니다."),
    MEETING_HOURS_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 인정시간을 입력했습니다."),
    MEETING_MAX_MEMBER_EXCEEDED(HttpStatus.BAD_REQUEST, "모임 유형별 최대 인원 제한을 초과했습니다."),
    MEETING_MAX_BELOW_CURRENT_MEMBER(HttpStatus.CONFLICT, "현재 참여 인원보다 정원을 적게 변경할 수 없습니다."),

    POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "봉사공고를 찾을 수 없습니다."),
    POSTING_CLOSED(HttpStatus.CONFLICT, "마감된 봉사공고입니다."),
    BOOKMARK_DUPLICATE(HttpStatus.CONFLICT, "이미 북마크한 공고입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다."),
    PARTICIPATION_DUPLICATE(HttpStatus.CONFLICT, "이미 신청한 봉사입니다."),
    PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND, "신청 내역을 찾을 수 없습니다."),
    PARTICIPATION_CANCEL_NOT_ALLOWED(
            HttpStatus.CONFLICT, "이력 보존을 위해 완료되었거나 후기가 작성된 신청은 취소할 수 없습니다."),
    POSTING_APPLICATION_UNAVAILABLE(HttpStatus.CONFLICT, "1365 신청 정보가 연동되지 않아 신청할 수 없는 공고입니다."),
    PARTICIPATION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료 처리된 참여입니다."),
    PARTICIPATION_COMPLETE_NOT_ALLOWED(HttpStatus.CONFLICT, "활동종료일이 지나야 완료 처리를 할 수 있습니다."),
    PARTICIPATION_HOURS_NOT_ALLOWED(HttpStatus.CONFLICT, "완료 처리된 참여만 인정시간을 입력할 수 있습니다."),
    PARTICIPATION_HOURS_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 인정시간을 입력했습니다."),

    UNSUPPORTED_PROFILE_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 프로필 이미지 형식입니다."),
    PROFILE_IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "프로필 이미지의 허용 크기를 초과했습니다."),
    INVALID_PROFILE_IMAGE_KEY(HttpStatus.BAD_REQUEST, "올바르지 않은 프로필 이미지 경로입니다."),
    PROFILE_IMAGE_OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드된 프로필 이미지 객체를 찾을 수 없습니다."),
    PROFILE_IMAGE_UPLOAD_EXPIRED(HttpStatus.BAD_REQUEST, "프로필 이미지 업로드 요청이 만료되었습니다."),
    PROFILE_IMAGE_UPLOAD_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, "처리되지 않은 프로필 이미지 업로드 요청이 너무 많습니다."),
    PROFILE_IMAGE_SIZE_MISMATCH(HttpStatus.BAD_REQUEST, "요청한 크기와 업로드된 이미지 크기가 다릅니다."),
    INVALID_PROFILE_IMAGE_CONTENT(HttpStatus.BAD_REQUEST, "실제 프로필 이미지 형식이 올바르지 않습니다."),
    PROFILE_IMAGE_UPLOAD_CONFLICT(HttpStatus.CONFLICT, "업로드된 프로필 이미지가 변경되어 다시 시도해야 합니다."),
    S3_OPERATION_FAILED(HttpStatus.BAD_GATEWAY, "이미지 저장소 연동에 실패했습니다."),

    MEETING_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "모임에 가입해야 이용할 수 있습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "게시글에 대한 권한이 없습니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "가입하지 않은 모임의 게시글은 열람할 수 없습니다."),
    NOTICE_HOST_ONLY(HttpStatus.FORBIDDEN, "공지는 모임장만 작성할 수 있습니다."),

    UNSUPPORTED_MEETING_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 모임 이미지 형식입니다."),
    MEETING_IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "모임 이미지의 허용 크기를 초과했습니다."),
    INVALID_MEETING_IMAGE_KEY(HttpStatus.BAD_REQUEST, "올바르지 않은 모임 이미지 경로입니다."),
    MEETING_IMAGE_UPLOAD_EXPIRED(HttpStatus.BAD_REQUEST, "모임 이미지 업로드 요청이 만료되었습니다."),
    MEETING_IMAGE_UPLOAD_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, "처리되지 않은 모임 이미지 업로드 요청이 너무 많습니다."),
    MEETING_IMAGE_SIZE_MISMATCH(HttpStatus.BAD_REQUEST, "요청한 크기와 업로드된 이미지 크기가 다릅니다."),
    INVALID_MEETING_IMAGE_CONTENT(HttpStatus.BAD_REQUEST, "실제 모임 이미지 형식이 올바르지 않습니다."),
    MEETING_IMAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "모임 이미지는 모임장만 변경할 수 있습니다."),
    MEETING_IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "모임 이미지는 최대 3장까지 등록할 수 있습니다."),
    MEETING_IMAGE_CONFLICT(HttpStatus.CONFLICT, "다른 요청이 모임 이미지를 변경했습니다. 다시 시도해주세요."),
    MEETING_IMAGE_OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드된 모임 이미지 객체를 찾을 수 없습니다."),
    MEETING_IMAGE_UPLOAD_CONFLICT(HttpStatus.CONFLICT, "이미 업로드된 모임 이미지 객체입니다."),

    // ── 게시글 댓글 ──
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "댓글에 대한 권한이 없습니다."),

    // ── 게시글 이미지 ──
    UNSUPPORTED_POST_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
    POST_IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지 크기가 허용 범위를 초과했습니다."),
    POST_IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지는 최대 3장까지 첨부할 수 있습니다."),
    INVALID_POST_IMAGE_KEY(HttpStatus.BAD_REQUEST, "유효하지 않은 이미지 키입니다."),
    POST_IMAGE_UPLOAD_EXPIRED(HttpStatus.BAD_REQUEST, "이미지 업로드 세션이 만료되었습니다."),
    POST_IMAGE_UPLOAD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "미반영 이미지 업로드가 너무 많습니다."),

    // ── 모임 나가기 ──
    MEETING_HOST_CANNOT_LEAVE(HttpStatus.CONFLICT, "모임장은 모임을 나갈 수 없습니다."),

    // ── 모임 해산 ──
    MEETING_DISBAND_HAS_CONFIRMED_PARTICIPANTS(
            HttpStatus.CONFLICT, "아직 종료되지 않은 모집 활동에 확정된 참가자가 있어 모임을 해산할 수 없습니다."),

    // ── 모집공고(RECRUIT) ──
    POST_RECRUIT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "모집공고는 전용 API로 작성해주세요."),
    RECRUIT_HOST_ONLY(HttpStatus.FORBIDDEN, "모집공고는 모임장만 작성할 수 있습니다."),
    RECRUIT_POSTING_BASED_NOT_ALLOWED(HttpStatus.FORBIDDEN, "공고 기반 모임은 자체 모집공고를 작성할 수 없습니다."),
    RECRUIT_NOT_FOUND(HttpStatus.NOT_FOUND, "모집공고를 찾을 수 없습니다."),
    RECRUIT_RECOGNIZED_MINUTES_REQUIRED(HttpStatus.BAD_REQUEST, "봉사시간 인정 시 인정 시간을 입력해야 합니다."),
    RECRUIT_APPLICATION_CLOSED(HttpStatus.CONFLICT, "신청 기간이 종료되어 변경할 수 없습니다."),
    RECRUIT_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "모집 정원이 가득 찼습니다."),
    RECRUIT_MAX_BELOW_APPLIED(HttpStatus.CONFLICT, "현재 신청 인원보다 정원을 적게 줄일 수 없습니다."),
    RECRUIT_PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND, "참여 신청을 찾을 수 없습니다."),
    RECRUIT_PARTICIPATION_CONFIRM_NOT_ALLOWED(HttpStatus.CONFLICT, "신청 상태의 참여만 확정할 수 있습니다."),
    RECRUIT_ACTIVITY_NOT_ENDED(HttpStatus.CONFLICT, "활동이 종료된 이후에만 참석 처리를 할 수 있습니다."),
    RECRUIT_PRESENT_NOT_ALLOWED(HttpStatus.CONFLICT, "확정된 참여만 참석 처리할 수 있습니다."),
    RECRUIT_INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "활동 시작 일시는 종료 일시보다 빨라야 합니다."),
    RECRUIT_INVALID_DEADLINE(HttpStatus.BAD_REQUEST, "신청 마감 일시는 활동 시작 일시보다 늦을 수 없습니다."),
    RECRUIT_CONFIRMED_LOCKED(HttpStatus.CONFLICT, "참가자가 확정된 이후에는 신청·취소할 수 없습니다."),
    RECRUIT_REAPPLY_NOT_ALLOWED(HttpStatus.CONFLICT, "반려된 신청은 다시 신청할 수 없습니다."),
    POST_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "내용 길이가 허용 범위를 초과했습니다."),

    // ── 멤버 관리(#11) ──
    MEETING_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "모임원을 찾을 수 없습니다."),
    MEETING_MEMBER_HOST_CANNOT_BE_REMOVED(HttpStatus.CONFLICT, "모임장은 내보낼 수 없습니다."),
    MEETING_MEMBER_HAS_ACTIVE_ACTIVITY(HttpStatus.CONFLICT, "아직 종료되지 않은 활동에 확정된 참가자는 내보낼 수 없습니다."),

    // ── 모집공고 신청자 관리(#13) ──
    RECRUIT_PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "신청자를 찾을 수 없습니다."),
    RECRUIT_PARTICIPANT_NOT_APPLIED(HttpStatus.CONFLICT, "신청(APPLIED) 상태의 참여만 처리할 수 있습니다."),
    RECRUIT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 모집공고입니다."),
    RECRUIT_NO_APPLICANTS_TO_CONFIRM(HttpStatus.CONFLICT, "확정할 신청자가 없습니다."),
    RECRUIT_ATTENDANCE_NOT_ALLOWED(
            HttpStatus.CONFLICT, "참가 인원 확정 후, 활동 종료 시각이 지난 뒤에만 출석 처리를 할 수 있습니다."),
    RECRUIT_ATTENDANCE_INVALID_STATUS(
            HttpStatus.BAD_REQUEST, "출석 상태는 PRESENT 또는 ABSENT만 지정할 수 있습니다."),

    // ── 활동 후기(REVIEW) ──
    POST_REVIEW_SOURCE_REQUIRED(
            HttpStatus.BAD_REQUEST, "후기는 근거 활동(reviewSourceType/reviewSourceId)이 필요합니다."),
    POST_REVIEW_ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND, "후기를 작성할 활동을 찾을 수 없습니다."),
    POST_REVIEW_ACTIVITY_NOT_COMPLETED(HttpStatus.CONFLICT, "완료된 활동에 대해서만 후기를 작성할 수 있습니다."),
    POST_REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 이 활동에 대한 후기가 존재합니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
