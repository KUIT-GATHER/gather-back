package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import java.time.LocalDateTime;

public record PostResponse(
        Long postId,
        Long meetingId,
        PostType type,
        String title,
        String content,
        Integer recruitCapacity,
        Long authorId,
        String authorNickname,
        Integer likeCount,
        Integer commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getMeeting().getId(),
                post.getType(),
                post.getTitle(),
                post.getContent(),
                post.getRecruitCapacity(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}