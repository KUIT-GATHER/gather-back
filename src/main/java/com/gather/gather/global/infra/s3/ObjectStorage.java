package com.gather.gather.global.infra.s3;

import java.time.Duration;

public interface ObjectStorage {

    String createPresignedPutUrl(
            String objectKey, String contentType, long contentLength, Duration expiration);

    StoredObjectMetadata getMetadata(String objectKey);

    byte[] getContent(String objectKey, String eTag);

    void delete(String objectKey);
}
