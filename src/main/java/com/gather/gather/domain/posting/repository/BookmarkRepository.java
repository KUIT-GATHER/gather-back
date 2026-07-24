package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);

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
}
