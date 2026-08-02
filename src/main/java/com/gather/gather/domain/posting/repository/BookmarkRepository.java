package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.notification.model.BookmarkedPostingDeadlineTarget;
import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);

    boolean existsByUserId(Long userId);

    long countByUserId(Long userId);

    Optional<Bookmark> findByUserIdAndPostingId(Long userId, Long postingId);

    /** Bookmark는 Posting과 연관관계 없이 FK id만 보관하므로(Bookmark.java 참고) 명시적 ON 절로 조인한다. */
    @Query(
            """
            select p from Posting p
            join Bookmark b on b.postingId = p.id
            where b.userId = :userId
              and (:category is null or p.category = :category)
              and (:keyword is null
                   or p.title like concat('%', :keyword, '%') escape '\\'
                   or p.recruitOrg like concat('%', :keyword, '%') escape '\\')
            order by b.createdAt desc, b.id desc
            """)
    Page<Posting> findBookmarkedPostings(
            @Param("userId") Long userId,
            @Param("category") PostingCategory category,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(
            """
            select new com.gather.gather.domain.notification.model.BookmarkedPostingDeadlineTarget(
                bookmark.userId,
                posting.id,
                posting.title
            )
            from Bookmark bookmark
            join Posting posting on posting.id = bookmark.postingId
            join User user on user.id = bookmark.userId
            where posting.noticeEndDate = :deadlineDate
              and posting.isActive = true
              and posting.status = com.gather.gather.domain.posting.entity.PostingStatus.RECRUITING
              and user.status = com.gather.gather.domain.auth.entity.UserStatus.ACTIVE
            """)
    List<BookmarkedPostingDeadlineTarget> findPostingDeadlineNotificationTargets(
            @Param("deadlineDate") LocalDate deadlineDate);
}
