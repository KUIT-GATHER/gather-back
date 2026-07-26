package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.entity.MeetingImageUpload;
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

    /**
     * 검증을 마친 이미지 세트를 모임에 반영한다. S3 검증은 락을 오래 잡지 않도록 호출 전에 끝내고, 여기서는 락 획득 후 상태만 다시 확인한다.
     *
     * @return 삭제 대기(SUPERSEDED)로 전환된 발급 건 id 목록
     */
    @Transactional
    public List<Long> apply(Long meetingId, Long userId, List<VerifiedMeetingImage> images) {
        Meeting meeting =
                meetingRepository
                        .findByIdForUpdate(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!meeting.getHost().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();

        // 1) 새로 업로드된 이미지: 발급 건을 잠그고 재검증한 뒤 APPLIED 로 소비한다.
        for (VerifiedMeetingImage image : images) {
            if (image.kept()) {
                continue;
            }
            MeetingImageUpload upload =
                    meetingImageUploadRepository
                            .findByMeetingIdAndObjectKeyForUpdate(meetingId, image.objectKey())
                            .orElseThrow(
                                    () -> new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY));
            upload.validatePendingSession(now, image.contentType());
            if (image.contentLength() != upload.getExpectedSize()) {
                throw new BusinessException(ErrorCode.MEETING_IMAGE_SIZE_MISMATCH);
            }
            upload.apply(now);
        }

        // 2) 기존 세트와 diff → 빠진 객체의 발급 건을 SUPERSEDED 로 표시(배치가 S3 삭제).
        List<MeetingImage> existing =
                meetingImageRepository.findByMeetingIdOrderBySortOrderAsc(meetingId);
        Set<String> newKeys =
                images.stream().map(VerifiedMeetingImage::objectKey).collect(Collectors.toSet());
        List<Long> superseded =
                existing.stream()
                        .map(MeetingImage::getObjectKey)
                        .filter(key -> !newKeys.contains(key))
                        .map(
                                key ->
                                        meetingImageUploadRepository
                                                .findByMeetingIdAndObjectKeyForUpdate(meetingId, key)
                                                .map(
                                                        upload -> {
                                                            upload.supersede();
                                                            return upload.getId();
                                                        })
                                                .orElse(null))
                        .filter(id -> id != null)
                        .toList();

        // 3) 이미지 세트 교체: 기존 행 삭제 후 순서대로 재삽입.
        //    같은 key 재삽입/정렬 유니크 충돌을 막기 위해 삭제를 먼저 flush 한다.
        meetingImageRepository.deleteAll(existing);
        meetingImageRepository.flush();
        int order = 0;
        for (VerifiedMeetingImage image : images) {
            meetingImageRepository.save(MeetingImage.create(meetingId, image.objectKey(), order++));
        }
        return superseded;
    }
}