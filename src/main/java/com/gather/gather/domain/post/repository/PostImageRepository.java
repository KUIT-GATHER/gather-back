package com.gather.gather.domain.post.repository;

import com.gather.gather.domain.post.entity.PostImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);

    /** 목록 화면에서 페이지의 게시글 이미지를 한 번에 조회하기 위한 배치 조회. */
    List<PostImage> findByPostIdInOrderByPostIdAscSortOrderAsc(Collection<Long> postIds);

    void deleteByPostId(Long postId);
}
