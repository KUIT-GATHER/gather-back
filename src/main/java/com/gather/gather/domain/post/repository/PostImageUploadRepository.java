package com.gather.gather.domain.post.repository;

import com.gather.gather.domain.post.entity.PostImageUpload;
import com.gather.gather.domain.post.entity.PostImageUploadStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageUploadRepository extends JpaRepository<PostImageUpload, Long> {

    Optional<PostImageUpload> findByUserIdAndObjectKey(Long userId, String objectKey);

    long countByUserIdAndStatusAndExpiresAtAfter(
            Long userId, PostImageUploadStatus status, LocalDateTime now);
}
