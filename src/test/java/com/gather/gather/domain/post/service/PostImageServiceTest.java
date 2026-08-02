package com.gather.gather.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.post.dto.PostImagePresignedUrlRequest;
import com.gather.gather.domain.post.dto.PostImagePresignedUrlResponse;
import com.gather.gather.domain.post.entity.PostImage;
import com.gather.gather.domain.post.entity.PostImageUpload;
import com.gather.gather.domain.post.entity.PostImageUploadStatus;
import com.gather.gather.domain.post.repository.PostImageRepository;
import com.gather.gather.domain.post.repository.PostImageUploadRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import com.gather.gather.global.util.SecurityUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POST_ID = 100L;
    private static final long IMAGE_SIZE = 1_048_576L;
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final String PUBLIC_BASE = "https://cdn.example";

    @Mock private PostImageRepository postImageRepository;
    @Mock private PostImageUploadRepository postImageUploadRepository;
    @Mock private ObjectStorage objectStorage;
    @Mock private S3Properties properties;

    @InjectMocks private PostImageService postImageService;

    private MockedStatic<SecurityUtil> securityUtil;

    @BeforeEach
    void setUp() {
        securityUtil = Mockito.mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtil.close();
    }

    @Test
    @DisplayName("presigned URL을 발급하고 posts/{userId}/ 키와 공개 URL을 반환한다")
    void createPresignedUrl_issuesUrl() {
        when(properties.maxImageSizeBytes()).thenReturn(MAX_SIZE);
        when(properties.presignedUrlExpirationSeconds()).thenReturn(300L);
        when(properties.publicBaseUrl()).thenReturn(PUBLIC_BASE);
        when(postImageUploadRepository.countByUserIdAndStatusAndExpiresAtAfter(
                        eq(USER_ID), eq(PostImageUploadStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(objectStorage.createPresignedPutUrl(
                        anyString(), eq("image/jpeg"), eq(IMAGE_SIZE), any(Duration.class)))
                .thenReturn("https://presigned.example/upload");

        PostImagePresignedUrlResponse response =
                postImageService.createPresignedUrl(
                        new PostImagePresignedUrlRequest("image/jpeg", IMAGE_SIZE));

        assertThat(response.uploadUrl()).isEqualTo("https://presigned.example/upload");
        assertThat(response.objectKey()).startsWith("posts/1/").endsWith(".jpg");
        assertThat(response.publicUrl()).isEqualTo(PUBLIC_BASE + "/" + response.objectKey());
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
        verify(postImageUploadRepository).save(any(PostImageUpload.class));
    }

    @Test
    @DisplayName("지원하지 않는 형식은 DB·S3 접근 전에 거부한다")
    void createPresignedUrl_rejectsUnsupportedType() {
        assertThatThrownBy(
                        () ->
                                postImageService.createPresignedUrl(
                                        new PostImagePresignedUrlRequest("image/gif", IMAGE_SIZE)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_POST_IMAGE_TYPE);
        verifyNoInteractions(postImageUploadRepository, objectStorage);
    }

    @Test
    @DisplayName("허용 크기를 초과하면 거부한다")
    void createPresignedUrl_rejectsOversized() {
        when(properties.maxImageSizeBytes()).thenReturn(MAX_SIZE);

        assertThatThrownBy(
                        () ->
                                postImageService.createPresignedUrl(
                                        new PostImagePresignedUrlRequest(
                                                "image/jpeg", MAX_SIZE + 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_IMAGE_SIZE_EXCEEDED);
        verifyNoInteractions(postImageUploadRepository, objectStorage);
    }

    @Test
    @DisplayName("이미지가 3장을 넘으면 반영을 거부한다")
    void setImages_rejectsMoreThanThree() {
        List<String> keys = List.of("a", "b", "c", "d");
        assertThatThrownBy(() -> postImageService.setImages(USER_ID, POST_ID, keys))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_IMAGE_COUNT_EXCEEDED);
        verifyNoInteractions(postImageRepository);
    }

    @Test
    @DisplayName("objectKeys가 null이면 아무 것도 하지 않는다(이미지 변경 없음)")
    void setImages_nullIsNoOp() {
        postImageService.setImages(USER_ID, POST_ID, null);
        verifyNoInteractions(postImageRepository, postImageUploadRepository);
    }

    @Test
    @DisplayName("신규 키는 PENDING 업로드를 반영(APPLIED)하고 post_image에 저장한다")
    void setImages_appliesNewKey() {
        String key = "posts/1/new.jpg";
        when(postImageRepository.findByPostIdOrderBySortOrderAsc(POST_ID)).thenReturn(List.of());
        PostImageUpload upload = Mockito.mock(PostImageUpload.class);
        when(upload.isPending()).thenReturn(true);
        when(upload.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(postImageUploadRepository.findByUserIdAndObjectKey(USER_ID, key))
                .thenReturn(Optional.of(upload));

        postImageService.setImages(USER_ID, POST_ID, List.of(key));

        verify(upload).apply(any(LocalDateTime.class));
        verify(postImageRepository).deleteByPostId(POST_ID);
        verify(postImageRepository).save(any(PostImage.class));
    }

    @Test
    @DisplayName("발급 이력이 없는 키는 INVALID_POST_IMAGE_KEY로 거부한다")
    void setImages_rejectsUnknownKey() {
        String key = "posts/1/unknown.jpg";
        when(postImageRepository.findByPostIdOrderBySortOrderAsc(POST_ID)).thenReturn(List.of());
        when(postImageUploadRepository.findByUserIdAndObjectKey(USER_ID, key))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postImageService.setImages(USER_ID, POST_ID, List.of(key)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_POST_IMAGE_KEY);
        verify(postImageRepository, never()).save(any(PostImage.class));
    }
}
