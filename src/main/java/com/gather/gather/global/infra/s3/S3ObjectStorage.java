package com.gather.gather.global.infra.s3;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
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
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
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
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.PROFILE_IMAGE_OBJECT_NOT_FOUND, exception);
            }
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
        }
    }

    @Override
    public byte[] getContent(String objectKey, String eTag) {
        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .ifMatch(eTag)
                        .build();
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.PROFILE_IMAGE_OBJECT_NOT_FOUND, exception);
            }
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request =
                DeleteObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build();
        try {
            s3Client.deleteObject(request);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return;
            }
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.S3_OPERATION_FAILED, exception);
        }
    }
}
