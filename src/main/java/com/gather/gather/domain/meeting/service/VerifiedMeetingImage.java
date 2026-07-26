package com.gather.gather.domain.meeting.service;

/**
 * 반영 검증을 통과한 이미지 한 장.
 *
 * <p>{@code kept=true}는 이미 반영돼 있던(재검증 불필요한) 기존 이미지, {@code kept=false}는 이번에 새로 업로드돼 S3 검증을 마친 이미지다.
 */
record VerifiedMeetingImage(String objectKey, String contentType, long contentLength, boolean kept) {

    static VerifiedMeetingImage uploaded(String objectKey, String contentType, long contentLength) {
        return new VerifiedMeetingImage(objectKey, contentType, contentLength, false);
    }

    static VerifiedMeetingImage kept(String objectKey, String contentType) {
        return new VerifiedMeetingImage(objectKey, contentType, 0L, true);
    }
}