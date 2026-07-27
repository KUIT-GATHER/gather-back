package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.user.dto.ProfileImageCurrentResponse;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlRequest;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlResponse;
import com.gather.gather.domain.user.dto.ProfileImageUpdateRequest;
import com.gather.gather.domain.user.dto.ProfileImageUpdateResponse;
import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.entity.ProfileImageUploadStatus;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import com.gather.gather.global.infra.s3.StoredObjectMetadata;
import com.gather.gather.global.util.SecurityUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    private static final Long USER_ID = 15L;
    private static final Long UPLOAD_ID = 99L;
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int IMAGE_SIZE = 1024;
    private static final String E_TAG = "\"test-etag\"";
    private static final String PUBLIC_BASE_URL =
            "https://test-profile-images.s3.ap-northeast-2.amazonaws.com";
    private static final String JPG_KEY = "profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg";
    private static final S3Properties PROPERTIES =
            new S3Properties(
                    "ap-northeast-2",
                    "test-profile-images",
                    PUBLIC_BASE_URL,
                    300,
                    20,
                    10,
                    MAX_SIZE,
                    "profiles",
                    "meetings",
                    3,
                    100,
                    3_600_000,
                    false);

    @Mock private UserRepository userRepository;
    @Mock private ProfileImageUploadRepository profileImageUploadRepository;
    @Mock private ObjectStorage objectStorage;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        profileImageService =
                new ProfileImageService(
                        userRepository,
                        profileImageUploadRepository,
                        objectStorage,
                        PROPERTIES,
                        new ProfileImageUrlResolver(PROPERTIES),
                        new ProfileImageContentValidator(),
                        new ProfileImageApplyService(
                                userRepository, profileImageUploadRepository, eventPublisher));
    }

    @Test
    @DisplayName("저장된 프로필 이미지 key를 공개 URL로 조회한다")
    void getCurrentProfileImage_returnsPublicUrl() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(JPG_KEY)));

        ProfileImageCurrentResponse response;
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            response = profileImageService.getCurrentProfileImage();
        }

        assertThat(response.profileImageUrl()).isEqualTo(PUBLIC_BASE_URL + "/" + JPG_KEY);
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 조회 URL은 null이다")
    void getCurrentProfileImage_returnsNull_whenImageIsNotRegistered() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));

        ProfileImageCurrentResponse response;
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            response = profileImageService.getCurrentProfileImage();
        }

        assertThat(response.profileImageUrl()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"image/jpeg,jpg", "image/png,png", "image/webp,webp"})
    @DisplayName("허용 MIME type은 잠긴 사용자와 추적 가능한 발급 건을 생성한다")
    void createPresignedUrl_createsTrackedUpload_forSupportedType(
            String contentType, String extension) {
        User user = user(null);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(profileImageUploadRepository.countByUserIdAndStatusAndExpiresAtAfter(
                        eq(USER_ID),
                        eq(ProfileImageUploadStatus.PENDING),
                        any(LocalDateTime.class)))
                .thenReturn(0L);
        when(objectStorage.createPresignedPutUrl(
                        anyString(),
                        eq(contentType),
                        eq((long) IMAGE_SIZE),
                        eq(Duration.ofSeconds(300))))
                .thenReturn("https://presigned.example/upload");

        ProfileImagePresignedUrlResponse response = createPresignedUrl(contentType, IMAGE_SIZE);

        assertThat(response.objectKey())
                .matches(
                        "profiles/15/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\."
                                + extension);
        assertThat(response.publicUrl()).isEqualTo(PUBLIC_BASE_URL + "/" + response.objectKey());
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        ArgumentCaptor<ProfileImageUpload> uploadCaptor =
                ArgumentCaptor.forClass(ProfileImageUpload.class);
        verify(profileImageUploadRepository).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(uploadCaptor.getValue().getObjectKey()).isEqualTo(response.objectKey());
        assertThat(uploadCaptor.getValue().getStatus()).isEqualTo(ProfileImageUploadStatus.PENDING);
    }

    @Test
    @DisplayName("Presigned URL 발급 요청마다 서로 다른 key를 생성한다")
    void createPresignedUrl_generatesDifferentObjectKey_perRequest() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(null)));
        when(profileImageUploadRepository.countByUserIdAndStatusAndExpiresAtAfter(
                        eq(USER_ID),
                        eq(ProfileImageUploadStatus.PENDING),
                        any(LocalDateTime.class)))
                .thenReturn(0L);
        when(objectStorage.createPresignedPutUrl(
                        anyString(), eq("image/jpeg"), eq((long) IMAGE_SIZE), any(Duration.class)))
                .thenReturn("https://presigned.example/upload");

        ProfileImagePresignedUrlResponse first = createPresignedUrl("image/jpeg", IMAGE_SIZE);
        ProfileImagePresignedUrlResponse second = createPresignedUrl("image/jpeg", IMAGE_SIZE);

        assertThat(first.objectKey()).isNotEqualTo(second.objectKey());
    }

    @Test
    @DisplayName("사용자가 없으면 Presigned URL을 발급하지 않는다")
    void createPresignedUrl_rejectsMissingUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> createPresignedUrl("image/jpeg", IMAGE_SIZE), ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(profileImageUploadRepository, objectStorage);
    }

    @Test
    @DisplayName("미반영 업로드가 사용자별 제한에 도달하면 추가 발급을 거부한다")
    void createPresignedUrl_rejectsPendingUploadLimit() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(null)));
        when(profileImageUploadRepository.countByUserIdAndStatusAndExpiresAtAfter(
                        eq(USER_ID),
                        eq(ProfileImageUploadStatus.PENDING),
                        any(LocalDateTime.class)))
                .thenReturn(3L);

        assertBusinessException(
                () -> createPresignedUrl("image/jpeg", IMAGE_SIZE),
                ErrorCode.PROFILE_IMAGE_UPLOAD_LIMIT_EXCEEDED);

        verify(profileImageUploadRepository, never()).save(any());
        verifyNoInteractions(objectStorage);
    }

    @Test
    @DisplayName("지원하지 않는 MIME type은 DB와 S3 접근 전에 거부한다")
    void createPresignedUrl_rejectsUnsupportedContentType() {
        assertBusinessException(
                () -> createPresignedUrl("image/gif", IMAGE_SIZE),
                ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);

        verifyNoInteractions(userRepository, profileImageUploadRepository, objectStorage);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("0 이하 파일 크기는 거부한다")
    void createPresignedUrl_rejectsNonPositiveSize(long fileSize) {
        assertBusinessException(
                () -> createPresignedUrl("image/jpeg", fileSize), ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(userRepository, profileImageUploadRepository, objectStorage);
    }

    @Test
    @DisplayName("정상 발급 건과 실제 JPEG 바이트는 profileImageKey로 반영한다")
    void updateProfileImage_appliesIssuedValidImage() {
        User user = user(null);
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareValidUpdate(user, upload, jpegBytes(IMAGE_SIZE));

        ProfileImageUpdateResponse response = update(JPG_KEY);

        assertThat(user.getProfileImageKey()).isEqualTo(JPG_KEY);
        assertThat(upload.getStatus()).isEqualTo(ProfileImageUploadStatus.APPLIED);
        assertThat(response.profileImageUrl()).isEqualTo(PUBLIC_BASE_URL + "/" + JPG_KEY);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("S3 검증을 마친 뒤 사용자와 발급 건을 순서대로 잠근다")
    void updateProfileImage_validatesS3BeforeLockingUserAndUploadSession() {
        User user = user(null);
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareValidUpdate(user, upload, jpegBytes(IMAGE_SIZE));

        update(JPG_KEY);

        InOrder inOrder = inOrder(objectStorage, userRepository, profileImageUploadRepository);
        inOrder.verify(objectStorage).getMetadata(JPG_KEY);
        inOrder.verify(objectStorage).getContent(JPG_KEY, E_TAG);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(profileImageUploadRepository)
                .findByUserIdAndObjectKeyForUpdate(USER_ID, JPG_KEY);
    }

    @Test
    @DisplayName("발급 기록이 없는 과거 key는 존재하는 S3 객체여도 반영하지 않는다")
    void updateProfileImage_rejectsUnissuedOrPreviousKey() {
        when(profileImageUploadRepository.findByUserIdAndObjectKey(USER_ID, JPG_KEY))
                .thenReturn(Optional.empty());

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.INVALID_PROFILE_IMAGE_KEY);

        verifyNoInteractions(objectStorage);
    }

    @Test
    @DisplayName("이미 소비된 발급 건은 다시 반영하지 않는다")
    void updateProfileImage_rejectsAlreadyAppliedUpload() {
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        upload.apply(null, LocalDateTime.now());
        prepareInitialSessionLookup(upload);

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.INVALID_PROFILE_IMAGE_KEY);

        verifyNoInteractions(userRepository, objectStorage);
    }

    @Test
    @DisplayName("만료된 발급 건은 반영하지 않는다")
    void updateProfileImage_rejectsExpiredUpload() {
        ProfileImageUpload upload =
                upload(JPG_KEY, "image/jpeg", IMAGE_SIZE, LocalDateTime.now().minusSeconds(1));
        prepareInitialSessionLookup(upload);

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.PROFILE_IMAGE_UPLOAD_EXPIRED);

        verifyNoInteractions(userRepository, objectStorage);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "profiles/16/550e8400-e29b-41d4-a716-446655440000.jpg",
                "profiles/15/../16/550e8400-e29b-41d4-a716-446655440000.jpg",
                "profiles/15/not-a-uuid.jpg",
                "profiles/15/550e8400-e29b-41d4-a716-446655440000.gif"
            })
    @DisplayName("다른 사용자 prefix와 비정상 key는 발급 건 조회 전에 거부한다")
    void updateProfileImage_rejectsInvalidObjectKey(String objectKey) {
        assertBusinessException(() -> update(objectKey), ErrorCode.INVALID_PROFILE_IMAGE_KEY);

        verifyNoInteractions(profileImageUploadRepository, objectStorage);
    }

    @Test
    @DisplayName("요청 크기와 실제 S3 객체 크기가 다르면 반영하지 않는다")
    void updateProfileImage_rejectsSizeMismatch() {
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareInitialSessionLookup(upload);
        when(objectStorage.getMetadata(JPG_KEY))
                .thenReturn(new StoredObjectMetadata("image/jpeg", IMAGE_SIZE - 1, E_TAG));

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.PROFILE_IMAGE_SIZE_MISMATCH);

        verify(objectStorage, never()).getContent(anyString(), anyString());
    }

    @Test
    @DisplayName("확장자와 S3 Content-Type이 다르면 반영하지 않는다")
    void updateProfileImage_rejectsStoredContentTypeMismatch() {
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareInitialSessionLookup(upload);
        when(objectStorage.getMetadata(JPG_KEY))
                .thenReturn(new StoredObjectMetadata("image/png", IMAGE_SIZE, E_TAG));

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.INVALID_PROFILE_IMAGE_KEY);

        verify(objectStorage, never()).getContent(anyString(), anyString());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("S3 객체 크기가 0이면 반영하지 않는다")
    void updateProfileImage_rejectsEmptyStoredObject() {
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareInitialSessionLookup(upload);
        when(objectStorage.getMetadata(JPG_KEY))
                .thenReturn(new StoredObjectMetadata("image/jpeg", 0, E_TAG));

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.VALIDATION_ERROR);

        verify(objectStorage, never()).getContent(anyString(), anyString());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("S3 객체가 최대 크기를 초과하면 반영하지 않는다")
    void updateProfileImage_rejectsStoredObjectOverMaximumSize() {
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareInitialSessionLookup(upload);
        when(objectStorage.getMetadata(JPG_KEY))
                .thenReturn(new StoredObjectMetadata("image/jpeg", MAX_SIZE + 1, E_TAG));

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.PROFILE_IMAGE_SIZE_EXCEEDED);

        verify(objectStorage, never()).getContent(anyString(), anyString());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("메타데이터는 JPEG지만 실제 바이트 시그니처가 아니면 반영하지 않는다")
    void updateProfileImage_rejectsDisguisedBinary() {
        User user = user(null);
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareValidObject(upload, new byte[IMAGE_SIZE]);

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.INVALID_PROFILE_IMAGE_CONTENT);

        assertThat(user.getProfileImageKey()).isNull();
        assertThat(upload.isPending()).isTrue();
    }

    @Test
    @DisplayName("HeadObject 이후 내려받은 바이트 크기가 바뀌면 반영하지 않는다")
    void updateProfileImage_rejectsContentChangedAfterHead() {
        User user = user(null);
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareInitialSessionLookup(upload);
        when(objectStorage.getMetadata(JPG_KEY))
                .thenReturn(new StoredObjectMetadata("image/jpeg", IMAGE_SIZE, E_TAG));
        when(objectStorage.getContent(JPG_KEY, E_TAG)).thenReturn(jpegBytes(IMAGE_SIZE - 1));

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.PROFILE_IMAGE_SIZE_MISMATCH);

        assertThat(user.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("S3 검증 중 다른 요청이 발급 건을 반영하면 잠금 획득 후 다시 거부한다")
    void updateProfileImage_revalidatesUploadSessionAfterS3Validation() {
        User user = user(null);
        ProfileImageUpload initialUpload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        ProfileImageUpload concurrentlyAppliedUpload =
                issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        concurrentlyAppliedUpload.apply(null, LocalDateTime.now());
        prepareValidObject(initialUpload, jpegBytes(IMAGE_SIZE));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(profileImageUploadRepository.findByUserIdAndObjectKeyForUpdate(USER_ID, JPG_KEY))
                .thenReturn(Optional.of(concurrentlyAppliedUpload));

        assertBusinessException(() -> update(JPG_KEY), ErrorCode.INVALID_PROFILE_IMAGE_KEY);

        assertThat(user.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("기존 이미지와 다른 새 이미지로 변경하면 영속 삭제 작업이 포함된 이벤트를 발행한다")
    void updateProfileImage_publishesDurablePreviousDeletionEvent() {
        String previousKey = "profiles/15/00000000-0000-0000-0000-000000000000.png";
        User user = user(previousKey);
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareValidUpdate(user, upload, jpegBytes(IMAGE_SIZE));

        update(JPG_KEY);

        ArgumentCaptor<ProfileImageReplacedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProfileImageReplacedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().uploadId()).isEqualTo(UPLOAD_ID);
        assertThat(upload.getPreviousObjectKey()).isEqualTo(previousKey);
        assertThat(upload.isPreviousObjectDeleted()).isFalse();
    }

    @Test
    @DisplayName("기존 key와 새 key가 같으면 삭제 작업을 만들지 않는다")
    void updateProfileImage_doesNotPublishDeletionEvent_whenKeyIsUnchanged() {
        User user = user(JPG_KEY);
        ProfileImageUpload upload = issuedUpload(JPG_KEY, "image/jpeg", IMAGE_SIZE);
        prepareValidUpdate(user, upload, jpegBytes(IMAGE_SIZE));

        update(JPG_KEY);

        verify(eventPublisher, never()).publishEvent(any());
        assertThat(upload.getPreviousObjectKey()).isNull();
        assertThat(upload.isPreviousObjectDeleted()).isTrue();
    }

    private ProfileImagePresignedUrlResponse createPresignedUrl(String contentType, long fileSize) {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            return profileImageService.createPresignedUrl(
                    new ProfileImagePresignedUrlRequest(contentType, fileSize));
        }
    }

    private ProfileImageUpdateResponse update(String objectKey) {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            return profileImageService.updateProfileImage(new ProfileImageUpdateRequest(objectKey));
        }
    }

    private void prepareValidUpdate(User user, ProfileImageUpload upload, byte[] content) {
        prepareSessionLookup(user, upload);
        prepareStoredObject(upload, content);
    }

    private void prepareValidObject(ProfileImageUpload upload, byte[] content) {
        prepareInitialSessionLookup(upload);
        prepareStoredObject(upload, content);
    }

    private void prepareStoredObject(ProfileImageUpload upload, byte[] content) {
        when(objectStorage.getMetadata(upload.getObjectKey()))
                .thenReturn(
                        new StoredObjectMetadata(
                                upload.getContentType(), upload.getExpectedSize(), E_TAG));
        when(objectStorage.getContent(upload.getObjectKey(), E_TAG)).thenReturn(content);
    }

    private void prepareSessionLookup(User user, ProfileImageUpload upload) {
        prepareInitialSessionLookup(upload);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(profileImageUploadRepository.findByUserIdAndObjectKeyForUpdate(
                        USER_ID, upload.getObjectKey()))
                .thenReturn(Optional.of(upload));
    }

    private void prepareInitialSessionLookup(ProfileImageUpload upload) {
        when(profileImageUploadRepository.findByUserIdAndObjectKey(USER_ID, upload.getObjectKey()))
                .thenReturn(Optional.of(upload));
    }

    private ProfileImageUpload issuedUpload(
            String objectKey, String contentType, long expectedSize) {
        return upload(objectKey, contentType, expectedSize, LocalDateTime.now().plusMinutes(5));
    }

    private ProfileImageUpload upload(
            String objectKey, String contentType, long expectedSize, LocalDateTime expiresAt) {
        ProfileImageUpload upload =
                ProfileImageUpload.create(
                        USER_ID,
                        objectKey,
                        contentType,
                        expectedSize,
                        expiresAt,
                        LocalDateTime.now());
        ReflectionTestUtils.setField(upload, "id", UPLOAD_ID);
        return upload;
    }

    private byte[] jpegBytes(int size) {
        byte[] content = new byte[size];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

    private User user(String profileImageKey) {
        try {
            var constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            User user = constructor.newInstance();
            ReflectionTestUtils.setField(user, "id", USER_ID);
            if (profileImageKey != null) {
                user.changeProfileImageKey(profileImageKey);
            }
            return user;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertBusinessException(Runnable operation, ErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }
}
