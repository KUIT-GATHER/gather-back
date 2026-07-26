package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingParticipationRepository extends JpaRepository<PostingParticipation, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);

    Optional<PostingParticipation> findByUserIdAndPostingId(Long userId, Long postingId);

    List<PostingParticipation> findByUserIdAndStatusNotIn(
            Long userId, Collection<PostingParticipationStatus> excludedStatuses);

    /** 회원 탈퇴 시 신청 이력을 전량 정리한다. */
    @Modifying
    @Query("DELETE FROM PostingParticipation p WHERE p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
