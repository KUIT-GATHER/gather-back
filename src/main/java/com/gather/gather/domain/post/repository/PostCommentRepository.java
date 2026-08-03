package com.gather.gather.domain.post.repository;

import com.gather.gather.domain.post.entity.PostComment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /** 게시글 상세의 댓글 목록. 작성자를 함께 로딩해 닉네임 접근 시 추가 쿼리를 막는다. 오래된 순으로 노출한다. */
    @Query(
            value =
                    """
                    SELECT c
                    FROM PostComment c
                    JOIN FETCH c.user
                    WHERE c.post.id = :postId
                      AND c.deletedAt IS NULL
                    ORDER BY c.createdAt ASC, c.id ASC
                    """,
            countQuery =
                    """
                    SELECT COUNT(c)
                    FROM PostComment c
                    WHERE c.post.id = :postId
                      AND c.deletedAt IS NULL
                    """)
    Page<PostComment> findVisibleByPostId(@Param("postId") Long postId, Pageable pageable);

    /** 수정·삭제용 단건 조회(미삭제). */
    @Query(
            """
            SELECT c
            FROM PostComment c
            JOIN FETCH c.user
            WHERE c.id = :commentId
              AND c.deletedAt IS NULL
            """)
    Optional<PostComment> findByIdFetchUser(@Param("commentId") Long commentId);

    /** 나의 활동 요약 - 내가 이 모임에서 (미삭제) 댓글을 단 게시글 수(게시글 기준 distinct). */
    @Query(
            """
            SELECT COUNT(DISTINCT c.post.id)
            FROM PostComment c
            WHERE c.post.meeting.id = :meetingId
              AND c.user.id = :userId
              AND c.deletedAt IS NULL
              AND c.post.deletedAt IS NULL
            """)
    long countCommentedPosts(@Param("meetingId") Long meetingId, @Param("userId") Long userId);
}
