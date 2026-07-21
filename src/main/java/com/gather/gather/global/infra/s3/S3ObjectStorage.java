package com.gather.gather.global.infra.s3;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@Slf4j
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    @Override
    public String createPresignedPutUrl(
            String objectKey, String contentType, long contentLength, Duration expiration) {
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        // 조건부 생성 조건까지 서명해 URL 재사용으로 기존 검증 객체를 덮어쓰지 못하게 한다.
                        .ifNoneMatch("*")
                        .build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(expiration)
                        .putObjectRequest(putObjectRequest)
                        .build();

        try {
            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (SdkException exception) {
            throw mapException("Presigned PUT URL 발급", objectKey, exception);
        }
    }

    @Override
    public StoredObjectMetadata getMetadata(String objectKey) {
        HeadObjectRequest request =
                HeadObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build();
        try {
            HeadObjectResponse response = s3Client.headObject(request);
            return new StoredObjectMetadata(
                    response.contentType(), response.contentLength(), response.eTag());
        } catch (SdkException exception) {
            throw mapException("HeadObject", objectKey, exception);
        }
    }

    @Override
    public byte[] getContent(String objectKey, String expectedVersion) {
        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        // HeadObject 이후 객체가 바뀌면 읽기를 거절해 검증 대상 바이트를 고정한다.
                        .ifMatch(expectedVersion)
                        .build();
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (SdkException exception) {
            if (isPreconditionFailed(exception)) {
                // HeadObject 이후 객체가 바뀌면 검증한 바이트와 달라진다. 서버 장애가 아니라
                // 클라이언트가 다시 업로드·반영해야 하는 상황이라 409로 돌리고 ERROR로 남기지 않는다.
                log.warn("검증 이후 프로필 이미지 객체가 변경되었습니다: objectKey={}", objectKey);
                throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_CONFLICT, exception);
            }
            throw mapException("GetObject", objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request =
                DeleteObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build();
        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            if (isNotFound(exception)) {
                return;
            }
            // 삭제는 항상 커밋 이후 비동기·배치 경로에서 호출되고 호출자가 uploadId와 함께 WARN을
            // 남긴 뒤 스케줄러가 재시도한다. 여기서 ERROR로 남기면 자가 치유되는 실패에 알림이 울린다.
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
        }
    }

    private boolean isNotFound(SdkException exception) {
        return exception instanceof S3Exception s3Exception && s3Exception.statusCode() == 404;
    }

    private boolean isPreconditionFailed(SdkException exception) {
        return exception instanceof S3Exception s3Exception && s3Exception.statusCode() == 412;
    }

    private BusinessException mapException(
            String operation, String objectKey, SdkException exception) {
        ErrorCode errorCode =
                isNotFound(exception)
                        ? ErrorCode.PROFILE_IMAGE_OBJECT_NOT_FOUND
                        : ErrorCode.S3_OPERATION_FAILED;
        if (errorCode == ErrorCode.PROFILE_IMAGE_OBJECT_NOT_FOUND) {
            log.debug("S3 객체를 찾을 수 없습니다: operation={}, objectKey={}", operation, objectKey);
            return new BusinessException(errorCode, exception);
        }
        if (exception instanceof S3Exception s3Exception) {
            log.error(
                    "S3 작업에 실패했습니다: operation={}, objectKey={}, statusCode={}",
                    operation,
                    objectKey,
                    s3Exception.statusCode(),
                    exception);
        } else {
            log.error("S3 작업에 실패했습니다: operation={}, objectKey={}", operation, objectKey, exception);
        }
        return new BusinessException(errorCode, exception);
    }
}
