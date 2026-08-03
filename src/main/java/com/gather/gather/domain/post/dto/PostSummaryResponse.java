package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시판 목록 카드용 응답.
 *
 * <p>본문 전체를 내려주고 "더보기" 노출 여부는 프론트에서 판단한다. 이미지가 있는 글은 목록 카드에서 첫 번째 이미지만 쓰지만, 응답에는 노출 순서대로 전체를 담아
 * 프론트가 선택하게 한다.
 */
public record PostSummaryResponse(
        Long postId,
        PostType type,
        String title,
        String content,
        Long authorId,
        String authorNickname,
        @Schema(description = "이미지 공개 URL 목록(노출 순서). 목록 카드는 첫 번째만 사용") List<String> imageUrls,
        Integer likeCount,
        Integer commentCount,
        @Schema(description = "조회자의 좋아요 여부") boolean liked,
        LocalDateTime createdAt) {

    public static PostSummaryResponse from(Post post, List<String> imageUrls, boolean liked) {
        return new PostSummaryResponse(
                post.getId(),
                post.getType(),
                post.getTitle(),
                post.getContent(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                imageUrls,
                post.getLikeCount(),
                post.getCommentCount(),
                liked,
                post.getCreatedAt());
    }
}
