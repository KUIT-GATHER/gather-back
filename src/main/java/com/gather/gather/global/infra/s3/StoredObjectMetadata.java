package com.gather.gather.global.infra.s3;

public record StoredObjectMetadata(String contentType, long contentLength, String eTag) {}
