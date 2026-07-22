package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingParticipationRepository extends JpaRepository<PostingParticipation, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);
}
