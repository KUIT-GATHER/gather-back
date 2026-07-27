package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingParticipationRepository extends JpaRepository<PostingParticipation, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);

    Optional<PostingParticipation> findByUserIdAndPostingId(Long userId, Long postingId);

    List<PostingParticipation> findByUserIdAndStatusNotIn(
            Long userId, Collection<PostingParticipationStatus> excludedStatuses);

    List<PostingParticipation> findByStatusIn(Collection<PostingParticipationStatus> statuses);

    List<PostingParticipation> findByUserIdAndStatusIn(
            Long userId, Collection<PostingParticipationStatus> statuses);
}
