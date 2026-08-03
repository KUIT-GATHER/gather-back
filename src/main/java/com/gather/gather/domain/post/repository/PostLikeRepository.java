package com.gather.gather.domain.post.repository;

import com.gather.gather.domain.post.entity.PostLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /** 목록 화면에서 조회자가 좋아요한 게시글을 한 번에 판별하기 위한 배치 조회. */
    @Query("SELECT l.postId FROM PostLike l WHERE l.userId = :userId AND l.postId IN :postIds")
    List<Long> findLikedPostIds(
            @Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
