package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);
}
