package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlRequest;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlResponse;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateRequest;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingImageUpload;
import com.gather.gather.domain.meeting.entity.MeetingImageUploadStatus;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.repository.MeetingImageUploadRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import com.gather.gather.global.infra.s3.StoredObjectMetadata;
import com.gather.gather.global.util.SecurityUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingImageServiceTest {

    private static final long MEETING_ID = 10L;
    private static final long HOST_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final int IMAGE_SIZE = 1024;
    private static final String PUBLIC_BASE_URL = "https://img.example";
    private static final String JPG_KEY = "meetings/10/11111111-1111-1111-1111-111111111111.jpg";
    private static final String EXISTING_KEY =
            "meetings/10/22222222-2222-2222-2222-222222222222.jpg";

    private static final S3Properties PROPERTIES =
            new S3Properties(
                    "ap-northeast-2",
                    "test-bucket",
                    PUBLIC_BASE_URL,
                    300,
                    20,
                    10,
                    5L * 1024 * 1024,
                    "profiles",
                    "meetings",
                    3,
                    100,
                    3_600_000,
                    false);

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingImageRepository meetingImageRepository;
    @Mock private MeetingImageUploadRepository meetingImageUploadRepository;
    @Mock private MeetingImageApplyService meetingImageApplyService;
    @Mock private ObjectStorage objectStorage;

    private MeetingImageService service;
    private MockedStatic<SecurityUtil> securityUtil;

    @BeforeEach
    void setUp() {
        service =
                new MeetingImageService(
                        meetingRepository,
                        meetingImageRepository,
                        meetingImageUploadRepository,
                        meetingImageApplyService,
                        new MeetingImageContentValidator(),
                        new MeetingImageUrlResolver(PROPERTIES),
                        objectStorage,
                        PROPERTIES);
        securityUtil = mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(HOST_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtil.close();
    }

    // ---------- createPresignedUrl ----------

    @Test
    @DisplayName("모임장이 정상 요청하면 meetings/{id}/ 경로의 발급 건을 저장하고 uploadUrl을 반환한다")
    void createPresignedUrl_success() {
        Meeting meeting = meetingOwnedBy(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingImageUploadRepository.countByMeetingIdAndStatusAndExpiresAtAfter(
                        eq(MEETING_ID),
                        eq(MeetingImageUploadStatus.PENDING),
                        any(LocalDateTime.class)))
                .thenReturn(0L);
        when(objectStorage.createPresignedPutUrl(
                        anyString(), eq("image/jpeg"), eq((long) IMAGE_SIZE), any(Duration.class)))
                .thenReturn("https://presigned.example/upload");

        MeetingImagePresignedUrlResponse response =
                service.createPresignedUrl(
                        MEETING_ID,
                        new MeetingImagePresignedUrlRequest("image/jpeg", (long) IMAGE_SIZE));

        assertThat(response.uploadUrl()).isEqualTo("https://presigned.example/upload");
        assertThat(response.objectKey()).startsWith("meetings/10/").endsWith(".jpg");
        assertThat(response.publicUrl()).isEqualTo(PUBLIC_BASE_URL + "/" + response.objectKey());

        ArgumentCaptor<MeetingImageUpload> captor =
                ArgumentCaptor.forClass(MeetingImageUpload.class);
        verify(meetingImageUploadRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MeetingImageUploadStatus.PENDING);
        assertThat(captor.getValue().getMeetingId()).isEqualTo(MEETING_ID);
        assertThat(captor.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(captor.getValue().getExpectedSize()).isEqualTo(IMAGE_SIZE);
    }

    @Test
    @DisplayName("지원하지 않는 MIME type은 DB·S3 접근 전에 거부한다")
    void createPresignedUrl_rejectsUnsupportedContentType() {
        assertBusinessException(
                () ->
                        service.createPresignedUrl(
                                MEETING_ID,
                                new MeetingImagePresignedUrlRequest(
                                        "image/gif", (long) IMAGE_SIZE)),
                ErrorCode.UNSUPPORTED_MEETING_IMAGE_TYPE);
        verifyNoInteractions(meetingRepository, meetingImageUploadRepository, objectStorage);
    }

    @Test
    @DisplayName("허용 크기를 초과하면 발급 전에 거부한다")
    void createPresignedUrl_rejectsOversizedFile() {
        assertBusinessException(
                () ->
                        service.createPresignedUrl(
                                MEETING_ID,
                                new MeetingImagePresignedUrlRequest(
                                        "image/jpeg", 6L * 1024 * 1024)),
                ErrorCode.MEETING_IMAGE_SIZE_EXCEEDED);
        verifyNoInteractions(meetingRepository, meetingImageUploadRepository, objectStorage);
    }

    @Test
    @DisplayName("모임장이 아니면 발급하지 않는다")
    void createPresignedUrl_rejectsNonHost() {
        Meeting meeting = meetingOwnedBy(OTHER_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));

        assertBusinessException(
                () ->
                        service.createPresignedUrl(
                                MEETING_ID,
                                new MeetingImagePresignedUrlRequest(
                                        "image/jpeg", (long) IMAGE_SIZE)),
                ErrorCode.MEETING_IMAGE_FORBIDDEN);
        verify(meetingImageUploadRepository, never()).save(any());
        verifyNoInteractions(objectStorage);
    }

    @Test
    @DisplayName("미반영 발급 제한에 도달하면 추가 발급을 거부한다")
    void createPresignedUrl_rejectsPendingLimit() {
        Meeting meeting = meetingOwnedBy(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingImageUploadRepository.countByMeetingIdAndStatusAndExpiresAtAfter(
                        eq(MEETING_ID),
                        eq(MeetingImageUploadStatus.PENDING),
                        any(LocalDateTime.class)))
                .thenReturn(3L);

        assertBusinessException(
                () ->
                        service.createPresignedUrl(
                                MEETING_ID,
                                new MeetingImagePresignedUrlRequest(
                                        "image/jpeg", (long) IMAGE_SIZE)),
                ErrorCode.MEETING_IMAGE_UPLOAD_LIMIT_EXCEEDED);
        verify(meetingImageUploadRepository, never()).save(any());
        verifyNoInteractions(objectStorage);
    }

    // ---------- updateImages ----------

    @Test
    @DisplayName("이미지가 3장을 넘으면 거부한다")
    void updateImages_rejectsMoreThanThree() {
        List<String> keys = List.of("a", "b", "c", "d");
        assertBusinessException(
                () -> service.updateImages(MEETING_ID, new MeetingImageUpdateRequest(keys)),
                ErrorCode.MEETING_IMAGE_COUNT_EXCEEDED);
        verifyNoInteractions(meetingRepository, meetingImageApplyService);
    }

    @Test
    @DisplayName("중복 objectKey는 거부한다")
    void updateImages_rejectsDuplicateKeys() {
        List<String> keys = List.of(JPG_KEY, JPG_KEY);
        assertBusinessException(
                () -> service.updateImages(MEETING_ID, new MeetingImageUpdateRequest(keys)),
                ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(meetingRepository, meetingImageApplyService);
    }

    @Test
    @DisplayName("모임장이 아니면 반영을 거부한다")
    void updateImages_rejectsNonHost() {
        Meeting meeting = meetingOwnedBy(OTHER_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));

        assertBusinessException(
                () ->
                        service.updateImages(
                                MEETING_ID, new MeetingImageUpdateRequest(List.of(JPG_KEY))),
                ErrorCode.MEETING_IMAGE_FORBIDDEN);
        verifyNoInteractions(meetingImageApplyService);
    }

    @Test
    @DisplayName("경로 규칙에 맞지 않는 objectKey는 거부한다")
    void updateImages_rejectsInvalidKeyFormat() {
        Meeting meeting = meetingOwnedBy(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));

        assertBusinessException(
                () ->
                        service.updateImages(
                                MEETING_ID,
                                new MeetingImageUpdateRequest(List.of("profiles/10/x.jpg"))),
                ErrorCode.INVALID_MEETING_IMAGE_KEY);
        verifyNoInteractions(meetingImageApplyService);
    }

    @Test
    @DisplayName("정상 업로드된 JPEG는 검증 후 반영하고 공개 URL을 반환한다")
    void updateImages_appliesValidUpload() {
        Meeting meeting = meetingOwnedBy(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingImageRepository.existsByMeetingIdAndObjectKey(MEETING_ID, JPG_KEY))
                .thenReturn(false);
        when(meetingImageUploadRepository.findByMeetingIdAndObjectKey(MEETING_ID, JPG_KEY))
                .thenReturn(Optional.of(pendingUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE)));
        StoredObjectMetadata meta = metadata("image/jpeg", IMAGE_SIZE, "etag");
        when(objectStorage.getMetadata(JPG_KEY)).thenReturn(meta);
        when(objectStorage.getContent(JPG_KEY, "etag")).thenReturn(jpegBytes(IMAGE_SIZE));

        MeetingImageUpdateResponse response =
                service.updateImages(MEETING_ID, new MeetingImageUpdateRequest(List.of(JPG_KEY)));

        assertThat(response.imageUrls()).containsExactly(PUBLIC_BASE_URL + "/" + JPG_KEY);
        verify(meetingImageApplyService).apply(eq(MEETING_ID), eq(HOST_ID), any());
    }

    @Test
    @DisplayName("실제 바이트가 선언한 형식과 다르면(매직바이트 불일치) 거부한다")
    void updateImages_rejectsMagicByteMismatch() {
        Meeting meeting = meetingOwnedBy(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingImageRepository.existsByMeetingIdAndObjectKey(MEETING_ID, JPG_KEY))
                .thenReturn(false);
        when(meetingImageUploadRepository.findByMeetingIdAndObjectKey(MEETING_ID, JPG_KEY))
                .thenReturn(Optional.of(pendingUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE)));
        StoredObjectMetadata meta = metadata("image/jpeg", IMAGE_SIZE, "etag");
        when(objectStorage.getMetadata(JPG_KEY)).thenReturn(meta);
        when(objectStorage.getContent(JPG_KEY, "etag")).thenReturn(new byte[IMAGE_SIZE]);

        assertBusinessException(
                () ->
                        service.updateImages(
                                MEETING_ID, new MeetingImageUpdateRequest(List.of(JPG_KEY))),
                ErrorCode.INVALID_MEETING_IMAGE_CONTENT);
        verify(meetingImageApplyService, never()).apply(any(), any(), any());
    }

    @Test
    @DisplayName("기존 이미지 유지 + 신규 업로드를 순서대로 반영한다(kept/uploaded 구분)")
    void updateImages_keepsExistingAndAddsNew() {
        Meeting meeting = meetingOwnedBy(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingImageRepository.existsByMeetingIdAndObjectKey(MEETING_ID, EXISTING_KEY))
                .thenReturn(true);
        when(meetingImageRepository.existsByMeetingIdAndObjectKey(MEETING_ID, JPG_KEY))
                .thenReturn(false);
        when(meetingImageUploadRepository.findByMeetingIdAndObjectKey(MEETING_ID, JPG_KEY))
                .thenReturn(Optional.of(pendingUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE)));
        StoredObjectMetadata meta = metadata("image/jpeg", IMAGE_SIZE, "etag");
        when(objectStorage.getMetadata(JPG_KEY)).thenReturn(meta);
        when(objectStorage.getContent(JPG_KEY, "etag")).thenReturn(jpegBytes(IMAGE_SIZE));

        service.updateImages(
                MEETING_ID, new MeetingImageUpdateRequest(List.of(EXISTING_KEY, JPG_KEY)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VerifiedMeetingImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(meetingImageApplyService).apply(eq(MEETING_ID), eq(HOST_ID), captor.capture());
        List<VerifiedMeetingImage> verified = captor.getValue();
        assertThat(verified).hasSize(2);
        assertThat(verified.get(0).objectKey()).isEqualTo(EXISTING_KEY);
        assertThat(verified.get(0).kept()).isTrue();
        assertThat(verified.get(1).objectKey()).isEqualTo(JPG_KEY);
        assertThat(verified.get(1).kept()).isFalse();
    }

    // ---------- getImages ----------

    @Test
    @DisplayName("소프트딜리트된 모임의 이미지 조회는 MEETING_NOT_FOUND를 반환한다")
    void getImages_rejectsDeletedMeeting() {
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.getImages(MEETING_ID), ErrorCode.MEETING_NOT_FOUND);
        verifyNoInteractions(meetingImageRepository);
    }

    // ---------- helpers ----------

    private Meeting meetingOwnedBy(Long hostId) {
        Meeting meeting = mock(Meeting.class);
        User host = mock(User.class);
        when(meeting.getHost()).thenReturn(host);
        when(host.getId()).thenReturn(hostId);
        return meeting;
    }

    private MeetingImageUpload pendingUpload(String key, String contentType, long size) {
        return MeetingImageUpload.create(
                MEETING_ID,
                HOST_ID,
                key,
                contentType,
                size,
                LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now());
    }

    private StoredObjectMetadata metadata(String contentType, long length, String eTag) {
        StoredObjectMetadata metadata = mock(StoredObjectMetadata.class);
        when(metadata.contentType()).thenReturn(contentType);
        when(metadata.contentLength()).thenReturn(length);
        when(metadata.eTag()).thenReturn(eTag);
        return metadata;
    }

    private byte[] jpegBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    private void assertBusinessException(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
