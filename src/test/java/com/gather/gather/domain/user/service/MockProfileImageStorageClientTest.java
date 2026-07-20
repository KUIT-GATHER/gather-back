package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.user.config.ProfileImageStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockProfileImageStorageClientTest {

    private final ProfileImageStorageProperties properties =
            new ProfileImageStorageProperties("gather-profile-images-dev", "ap-northeast-2");
    private final MockProfileImageStorageClient client =
            new MockProfileImageStorageClient(properties);

    @Test
    @DisplayName("buildPublicUrl assembles the S3 public URL from bucket, region, and object key")
    void buildPublicUrl_assemblesS3Url() {
        String url = client.buildPublicUrl("profiles/1/uuid.jpg");

        assertThat(url)
                .isEqualTo(
                        "https://gather-profile-images-dev.s3.ap-northeast-2.amazonaws.com/profiles/1/uuid.jpg");
    }

    @Test
    @DisplayName(
            "createUploadUrl returns a mock URL built on top of the public URL with the expiry set")
    void createUploadUrl_returnsMockUrlWithExpiry() {
        ProfileImageStorageClient.ProfileImageUploadUrl uploadUrl =
                client.createUploadUrl("profiles/1/uuid.jpg", "image/jpeg");

        assertThat(uploadUrl.uploadUrl())
                .startsWith(
                        "https://gather-profile-images-dev.s3.ap-northeast-2.amazonaws.com/profiles/1/uuid.jpg?mock-presigned=true")
                .contains("contentType=image/jpeg");
        assertThat(uploadUrl.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    @DisplayName("deleteObject does not throw even though the object was never really uploaded")
    void deleteObject_doesNotThrow() {
        client.deleteObject("profiles/1/uuid.jpg");
    }
}
