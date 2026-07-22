package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Bookmark;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);

    boolean existsByUserId(Long userId);

    Optional<Bookmark> findByUserIdAndPostingId(Long userId, Long postingId);
}
