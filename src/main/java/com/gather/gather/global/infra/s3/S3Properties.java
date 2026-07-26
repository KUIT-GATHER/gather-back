package com.gather.gather.global.infra.s3;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gather.aws.s3")
public record S3Properties(
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String publicBaseUrl,
        @Positive long presignedUrlExpirationSeconds,
        @Positive long apiCallTimeoutSeconds,
        @Positive long apiCallAttemptTimeoutSeconds,
        @Positive long maxImageSizeBytes,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]+") String objectPrefix,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]+") String meetingObjectPrefix,
        @Positive int maxPendingUploadsPerUser,
        @Positive int cleanupBatchSize,
        @Positive long cleanupFixedDelayMilliseconds,
        boolean cleanupSchedulerEnabled) {}
