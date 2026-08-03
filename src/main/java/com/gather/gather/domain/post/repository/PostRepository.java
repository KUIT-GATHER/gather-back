package com.gather.gather.domain.post.repository;

import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 상세 조회. 작성자를 함께 로딩해 닉네임 접근 시 추가 쿼리를 막는다. */
    @Query(
            """
            SELECT p
            FROM Post p
            JOIN FETCH p.user
            WHERE p.id = :postId
              AND p.deletedAt IS NULL
            """)
    Optional<Post> findByIdFetchUser(@Param("postId") Long postId);

    /**
     * 특정 모임의 게시글을 유형 필터로 페이지 조회한다.
     *
     * <p>가입자는 전체 유형, 미가입자는 {@link PostType#visibleToNonMember()}만 넘겨 호출한다. 정렬은 컨트롤러의 {@code
     * PageableDefault}(createdAt,id DESC)를 따른다. p.user는 단일 연관이라 fetch join + 페이지네이션이 안전하다.
     */
    @Query(
            value =
                    """
                    SELECT p
                    FROM Post p
                    JOIN FETCH p.user
                    WHERE p.meeting.id = :meetingId
                      AND p.deletedAt IS NULL
                      AND p.type IN :types
                    """,
            countQuery =
                    """
                    SELECT COUNT(p)
                    FROM Post p
                    WHERE p.meeting.id = :meetingId
                      AND p.deletedAt IS NULL
                      AND p.type IN :types
                    """)
    Page<Post> findVisiblePosts(
            @Param("meetingId") Long meetingId,
            @Param("types") Collection<PostType> types,
            Pageable pageable);

    /** 나의 활동 - 내가 이 모임에서 작성한 게시글. */
    @Query(
            value =
                    """
                    SELECT p
                    FROM Post p
                    JOIN FETCH p.user
                    WHERE p.meeting.id = :meetingId
                      AND p.user.id = :userId
                      AND p.deletedAt IS NULL
                    """,
            countQuery =
                    """
                    SELECT COUNT(p)
                    FROM Post p
                    WHERE p.meeting.id = :meetingId
                      AND p.user.id = :userId
                      AND p.deletedAt IS NULL
                    """)
    Page<Post> findMyPosts(
            @Param("meetingId") Long meetingId, @Param("userId") Long userId, Pageable pageable);

    /** 나의 활동 - 내가 이 모임에서 (미삭제) 댓글을 단 게시글. */
    @Query(
            value =
                    """
                    SELECT p
                    FROM Post p
                    JOIN FETCH p.user
                    WHERE p.meeting.id = :meetingId
                      AND p.deletedAt IS NULL
                      AND p.id IN (
                          SELECT c.post.id
                          FROM PostComment c
                          WHERE c.user.id = :userId
                            AND c.deletedAt IS NULL
                      )
                    """,
            countQuery =
                    """
                    SELECT COUNT(p)
                    FROM Post p
                    WHERE p.meeting.id = :meetingId
                      AND p.deletedAt IS NULL
                      AND p.id IN (
                          SELECT c.post.id
                          FROM PostComment c
                          WHERE c.user.id = :userId
                            AND c.deletedAt IS NULL
                      )
                    """)
    Page<Post> findMyCommentedPosts(
            @Param("meetingId") Long meetingId, @Param("userId") Long userId, Pageable pageable);

    /** 나의 활동 탭 요약 - 내가 이 모임에서 작성한 게시글 수. */
    long countByMeeting_IdAndUser_IdAndDeletedAtIsNull(Long meetingId, Long userId);

    /** 뱃지 진행률 조회용 — 전체 모임을 통틀어 내가 작성한 특정 유형의 게시글 수(FIRST_REVIEW). */
    long countByUser_IdAndTypeAndDeletedAtIsNull(Long userId, PostType type);
}
