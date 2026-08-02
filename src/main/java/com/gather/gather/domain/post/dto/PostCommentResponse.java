package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.entity.PostComment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 게시글 댓글 목록·단건 응답. {@code canEdit}/{@code canDelete}로 프론트 UI 제어를 돕는다. */
public record PostCommentResponse(
        @Schema(description = "댓글 ID", example = "1") Long commentId,
        @Schema(description = "작성자 ID", example = "7") Long authorId,
        @Schema(description = "작성자 닉네임", example = "박서준") String authorNickname,
        @Schema(description = "댓글 내용") String content,
        @Schema(description = "작성 시각") LocalDateTime createdAt,
        @Schema(description = "수정 시각(미수정 시 생성 시각과 동일)") LocalDateTime updatedAt,
        @Schema(description = "조회자가 이 댓글을 수정할 수 있는지") boolean canEdit,
        @Schema(description = "조회자가 이 댓글을 삭제할 수 있는지") boolean canDelete) {

    public static PostCommentResponse from(PostComment comment, boolean canEdit, boolean canDelete) {
        return new PostCommentResponse(
                comment.getId(),
                comment.getUser().getId(),
                comment.getUser().getNickname(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                canEdit,
                canDelete);
    }
}
