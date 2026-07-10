package com.gather.gather.domain.post.repository;

import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
     * 특정 모임의 게시글을 유형 필터로 조회한다.
     *
     * <p>가입자는 전체 유형, 미가입자는 {@link PostType#visibleToNonMember()}만 넘겨 호출한다.
     */
    @Query(
            """
            SELECT p
            FROM Post p
            JOIN FETCH p.user
            WHERE p.meeting.id = :meetingId
              AND p.deletedAt IS NULL
              AND p.type IN :types
            ORDER BY p.createdAt DESC
            """)
    List<Post> findVisiblePosts(
            @Param("meetingId") Long meetingId, @Param("types") Collection<PostType> types);
}
