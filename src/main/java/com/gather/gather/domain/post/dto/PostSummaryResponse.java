package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import java.time.LocalDateTime;

/**
 * 게시판 목록 카드용 응답.
 *
 * <p>본문 전체를 내려주고 "더보기" 노출 여부는 프론트에서 판단. 게시글 이미지는 현재 ERD {@code post} 테이블에 컬럼 안만들어놨음!
 */
public record PostSummaryResponse(
        Long postId,
        PostType type,
        String title,
        String content,
        Long authorId,
        String authorNickname,
        Integer likeCount,
        Integer commentCount,
        LocalDateTime createdAt) {

    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getType(),
                post.getTitle(),
                post.getContent(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt());
    }
}