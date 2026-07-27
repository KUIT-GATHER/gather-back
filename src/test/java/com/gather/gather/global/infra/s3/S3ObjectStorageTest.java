package com.gather.gather.global.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageTest {

    private static final String BUCKET = "test-profile-images";
    private static final String OBJECT_KEY = "profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg";
    private static final S3Properties PROPERTIES =
            new S3Properties(
                    "ap-northeast-2",
                    BUCKET,
                    "https://test-profile-images.example",
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

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedPutObjectRequest presignedRequest;

    private S3ObjectStorage objectStorage;

    @BeforeEach
    void setUp() {
        objectStorage = new S3ObjectStorage(s3Client, s3Presigner, PROPERTIES);
    }

    @Test
    @DisplayName("Presigned PUT 요청에는 bucket, key, Content-Type, Content-Length, 만료 시간이 포함된다")
    void createPresignedPutUrl_buildsConstrainedPutRequest() throws Exception {
        when(presignedRequest.url())
                .thenReturn(URI.create("https://presigned.example/upload").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        String url =
                objectStorage.createPresignedPutUrl(
                        OBJECT_KEY, "image/jpeg", 1024, Duration.ofSeconds(300));

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectPresignRequest request = captor.getValue();
        assertThat(request.signatureDuration()).isEqualTo(Duration.ofSeconds(300));
        assertThat(request.putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(request.putObjectRequest().key()).isEqualTo(OBJECT_KEY);
        assertThat(request.putObjectRequest().contentType()).isEqualTo("image/jpeg");
        assertThat(request.putObjectRequest().contentLength()).isEqualTo(1024);
        assertThat(request.putObjectRequest().ifNoneMatch()).isEqualTo("*");
        assertThat(url).isEqualTo("https://presigned.example/upload");
    }

    @Test
    @DisplayName("실제 AWS Presigner는 If-None-Match를 필수 서명 헤더로 포함한다")
    void createPresignedPutUrl_signsIfNoneMatchHeader_withRealPresigner() {
        try (S3Presigner realPresigner =
                S3Presigner.builder()
                        .region(Region.AP_NORTHEAST_2)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                "test-access-key", "test-secret-key")))
                        .build()) {
            S3ObjectStorage storage = new S3ObjectStorage(s3Client, realPresigner, PROPERTIES);

            String url =
                    storage.createPresignedPutUrl(
                            OBJECT_KEY, "image/jpeg", 1024, Duration.ofSeconds(300));

            assertThat(URLDecoder.decode(url, StandardCharsets.UTF_8))
                    .contains("X-Amz-SignedHeaders=")
                    .contains("if-none-match");
        }
    }

    @Test
    @DisplayName("HeadObject 응답의 MIME type과 실제 크기를 반환한다")
    void getMetadata_returnsHeadObjectMetadata() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(
                        HeadObjectResponse.builder()
                                .contentType("image/jpeg")
                                .contentLength(1024L)
                                .eTag("\"test-etag\"")
                                .build());

        StoredObjectMetadata metadata = objectStorage.getMetadata(OBJECT_KEY);

        assertThat(metadata.contentType()).isEqualTo("image/jpeg");
        assertThat(metadata.contentLength()).isEqualTo(1024);
        assertThat(metadata.eTag()).isEqualTo("\"test-etag\"");
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    @DisplayName("GetObject는 HeadObject에서 확인한 ETag 조건으로 동일 바이트를 내려받는다")
    void getContent_usesETagPrecondition() {
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

        byte[] result = objectStorage.getContent(OBJECT_KEY, "\"test-etag\"");

        assertThat(result).containsExactly(content);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(OBJECT_KEY);
        assertThat(captor.getValue().ifMatch()).isEqualTo("\"test-etag\"");
    }

    @Test
    @DisplayName("GetObject 404는 객체 없음 오류로 변환한다")
    void getContent_maps404ToObjectNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertBusinessException(
                () -> objectStorage.getContent(OBJECT_KEY, "\"test-etag\""),
                ErrorCode.PROFILE_IMAGE_OBJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("GetObject의 404 이외 S3 오류는 저장소 연동 오류로 변환한다")
    void getContent_mapsOtherS3FailureToStorageFailure() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("forbidden").build());

        assertBusinessException(
                () -> objectStorage.getContent(OBJECT_KEY, "\"test-etag\""),
                ErrorCode.S3_OPERATION_FAILED);
    }

    @Test
    @DisplayName("GetObject 412는 검증 이후 객체 변경 충돌로 변환한다")
    void getContent_maps412ToUploadConflict() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(412).message("precondition").build());

        assertBusinessException(
                () -> objectStorage.getContent(OBJECT_KEY, "\"test-etag\""),
                ErrorCode.PROFILE_IMAGE_UPLOAD_CONFLICT);
    }

    @Test
    @DisplayName("GetObject SDK 오류는 저장소 연동 오류로 변환한다")
    void getContent_mapsClientFailureToStorageFailure() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("connection failed"));

        assertBusinessException(
                () -> objectStorage.getContent(OBJECT_KEY, "\"test-etag\""),
                ErrorCode.S3_OPERATION_FAILED);
    }

    @Test
    @DisplayName("HeadObject 404는 객체 부재 오류로 변환한다")
    void getMetadata_maps404ToObjectNotFound() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertBusinessException(
                () -> objectStorage.getMetadata(OBJECT_KEY),
                ErrorCode.PROFILE_IMAGE_OBJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("S3 클라이언트 장애는 내부 정보 없이 저장소 연동 오류로 변환한다")
    void getMetadata_mapsClientFailureToStorageFailure() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.create("connection failed"));

        assertBusinessException(
                () -> objectStorage.getMetadata(OBJECT_KEY), ErrorCode.S3_OPERATION_FAILED);
    }

    @Test
    @DisplayName("DeleteObject 요청에는 설정된 bucket과 기존 key가 포함된다")
    void delete_usesConfiguredBucketAndObjectKey() {
        objectStorage.delete(OBJECT_KEY);

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    @DisplayName("DeleteObject 404는 이미 삭제된 상태로 간주한다")
    void delete_ignoresNotFound() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        objectStorage.delete(OBJECT_KEY);
    }

    @Test
    @DisplayName("DeleteObject의 404 이외 S3 오류는 저장소 연동 오류로 변환한다")
    void delete_mapsOtherS3FailureToStorageFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("forbidden").build());

        assertBusinessException(
                () -> objectStorage.delete(OBJECT_KEY), ErrorCode.S3_OPERATION_FAILED);
    }

    @Test
    @DisplayName("DeleteObject SDK 오류는 저장소 연동 오류로 변환한다")
    void delete_mapsClientFailureToStorageFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("connection failed"));

        assertBusinessException(
                () -> objectStorage.delete(OBJECT_KEY), ErrorCode.S3_OPERATION_FAILED);
    }

    private void assertBusinessException(Runnable operation, ErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }
}
