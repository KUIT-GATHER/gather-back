package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.entity.MeetingImageUpload;
import com.gather.gather.domain.meeting.entity.MeetingImageUploadStatus;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.repository.MeetingImageUploadRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingImageApplyService {

    private final MeetingRepository meetingRepository;
    private final MeetingImageRepository meetingImageRepository;
    private final MeetingImageUploadRepository meetingImageUploadRepository;

    /** 검증을 마친 이미지 세트를 모임에 반영한다. S3 검증은 호출 전에 끝내고, 여기서는 락 획득 후 상태만 다시 확인한다. */
    @Transactional
    public void apply(Long meetingId, Long userId, List<VerifiedMeetingImage> images) {
        Meeting meeting =
                meetingRepository
                        .findByIdAndDeletedAtIsNullForUpdate(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!meeting.getHost().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();

        // 1) 이미지별 처리 (모임 락 보유 상태)
        for (VerifiedMeetingImage image : images) {
            if (image.kept()) {
                // Blocker 3: 락 이후 재검증. 그 사이 다른 요청이 제거(SUPERSEDED)했다면 깨진 URL을 만들지 않고 실패시킨다.
                boolean stillLive =
                        meetingImageRepository.existsByMeetingIdAndObjectKey(
                                meetingId, image.objectKey());
                boolean stillApplied =
                        meetingImageUploadRepository
                                .findByMeetingIdAndObjectKeyForUpdate(meetingId, image.objectKey())
                                .filter(u -> u.getStatus() == MeetingImageUploadStatus.APPLIED)
                                .isPresent();
                if (!stillLive || !stillApplied) {
                    throw new BusinessException(ErrorCode.MEETING_IMAGE_CONFLICT);
                }
                continue;
            }
            MeetingImageUpload upload =
                    meetingImageUploadRepository
                            .findByMeetingIdAndObjectKeyForUpdate(meetingId, image.objectKey())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    ErrorCode.INVALID_MEETING_IMAGE_KEY));
            upload.validatePendingSession(now, image.contentType());
            if (image.contentLength() != upload.getExpectedSize()) {
                throw new BusinessException(ErrorCode.MEETING_IMAGE_SIZE_MISMATCH);
            }
            upload.apply(now);
        }

        // 2) 기존 세트와 diff → 빠진 객체의 발급 건을 SUPERSEDED로 표시(배치가 S3 삭제).
        List<MeetingImage> existing =
                meetingImageRepository.findByMeetingIdOrderBySortOrderAsc(meetingId);
        Set<String> newKeys =
                images.stream().map(VerifiedMeetingImage::objectKey).collect(Collectors.toSet());
        for (MeetingImage old : existing) {
            if (newKeys.contains(old.getObjectKey())) {
                continue;
            }
            meetingImageUploadRepository
                    .findByMeetingIdAndObjectKeyForUpdate(meetingId, old.getObjectKey())
                    .ifPresent(MeetingImageUpload::supersede);
        }

        // 3) 이미지 세트 교체: 기존 행 삭제 후 순서대로 재삽입(유니크 충돌 방지 위해 flush).
        meetingImageRepository.deleteAll(existing);
        meetingImageRepository.flush();
        int order = 0;
        for (VerifiedMeetingImage image : images) {
            meetingImageRepository.save(MeetingImage.create(meetingId, image.objectKey(), order++));
        }
    }
}
