package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.enums.ReviewSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        Long postId,
        Long meetingId,
        PostType type,
        String title,
        String content,
        Integer recruitCapacity,
        Long authorId,
        String authorNickname,
        @Schema(description = "이미지 공개 URL 목록(노출 순서, 최대 3장)") List<String> imageUrls,
        Integer likeCount,
        Integer commentCount,
        @Schema(description = "조회자의 좋아요 여부") boolean liked,
        @Schema(description = "조회자가 이 글을 수정할 수 있는지(작성자 본인)") boolean canEdit,
        @Schema(description = "조회자가 이 글을 삭제할 수 있는지(작성자 본인 또는 모임장)") boolean canDelete,
        @Schema(description = "후기(REVIEW)일 때만 값이 있음 - 근거 활동 출처") ReviewSourceType reviewSourceType,
        @Schema(description = "후기(REVIEW)일 때만 값이 있음 - 근거 활동 ID") Long reviewSourceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PostResponse from(
            Post post, List<String> imageUrls, boolean liked, boolean canEdit, boolean canDelete) {
        return new PostResponse(
                post.getId(),
                post.getMeeting().getId(),
                post.getType(),
                post.getTitle(),
                post.getContent(),
                post.getRecruitCapacity(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                imageUrls,
                post.getLikeCount(),
                post.getCommentCount(),
                liked,
                canEdit,
                canDelete,
                post.getReviewSourceType(),
                post.getReviewSourceId(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
