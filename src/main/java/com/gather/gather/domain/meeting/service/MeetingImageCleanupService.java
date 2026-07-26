package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.entity.MeetingImageUpload;
import com.gather.gather.domain.meeting.entity.MeetingImageUploadStatus;
import com.gather.gather.domain.meeting.repository.MeetingImageUploadRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "gather.aws.s3",
        name = "cleanup-scheduler-enabled",
        havingValue = "true")
public class MeetingImageCleanupService {

    private final MeetingImageUploadRepository meetingImageUploadRepository;
    private final ObjectStorage objectStorage;
    private final S3Properties properties;

    @Scheduled(fixedDelayString = "${gather.aws.s3.cleanup-fixed-delay-milliseconds}")
    public void cleanup() {
        deleteExpiredPendingUploads();
        deleteSupersededObjects();
    }

    /** 반영되지 않고 만료된 발급 건: S3 객체와 추적 행을 제거한다. */
    @Transactional
    public int deleteExpiredPendingUploads() {
        Pageable page = PageRequest.of(0, properties.cleanupBatchSize());
        List<MeetingImageUpload> expired =
                meetingImageUploadRepository.findExpiredForUpdate(
                        MeetingImageUploadStatus.PENDING, LocalDateTime.now(), page);
        int count = 0;
        for (MeetingImageUpload upload : expired) {
            try {
                objectStorage.delete(upload.getObjectKey());
                meetingImageUploadRepository.delete(upload);
                count++;
            } catch (BusinessException exception) {
                log.warn(
                        "만료된 모임 이미지 객체 삭제 실패(다음 배치 재시도): objectKey={}",
                        upload.getObjectKey(),
                        exception);
            }
        }
        return count;
    }

    /** 교체로 밀려난 객체: S3 삭제에 성공하면 추적 행을 제거하고, 실패하면 다음 배치가 재시도한다. */
    @Transactional
    public int deleteSupersededObjects() {
        Pageable page = PageRequest.of(0, properties.cleanupBatchSize());
        List<MeetingImageUpload> superseded =
                meetingImageUploadRepository.findDeletionPendingForUpdate(
                        MeetingImageUploadStatus.SUPERSEDED, page);
        int count = 0;
        for (MeetingImageUpload upload : superseded) {
            try {
                objectStorage.delete(upload.getObjectKey());
                meetingImageUploadRepository.delete(upload);
                count++;
            } catch (BusinessException exception) {
                log.warn(
                        "교체된 모임 이미지 객체 삭제 실패(다음 배치 재시도): objectKey={}",
                        upload.getObjectKey(),
                        exception);
            }
        }
        return count;
    }
}
