package com.gather.gather.domain.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 프로필 사진 S3 버킷 설정. {@code @ConfigurationPropertiesScan}으로 자동 등록된다.
 *
 * <p>비밀값이 아니라 버킷명·리전뿐이다(EC2 IAM Role로 인증하므로 access key/secret 불필요, devplan2 2-1-1절). 버킷이 아직 생성되지 않은
 * 동안은 {@link com.gather.gather.domain.user.service.MockProfileImageStorageClient}가 이 값을 그대로 문자열
 * 조립에만 사용한다.
 */
@ConfigurationProperties(prefix = "s3.profile-image")
public record ProfileImageStorageProperties(
        @DefaultValue("gather-profile-images-dev") String bucket,
        @DefaultValue("ap-northeast-2") String region) {}
