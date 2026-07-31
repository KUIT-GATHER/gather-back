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

    /** 뱃지 판정(완료 횟수, 연속 참여 월)용 — 완료된 참여 이력만 조회한다. */
    List<PostingParticipation> findAllByUserIdAndStatus(
            Long userId, PostingParticipationStatus status);

    /**
     * COMPLETED뿐 아니라 REVIEWED(후기 작성됨)까지 포함해 조회한다 — 이 리포 전체가 두 상태를 함께 "완료"로 취급하므로( {@code
     * CALENDAR_EXCLUDED_STATUSES}, {@code PostingParticipationAction} 등과 동일 정책), 뱃지 판정과 마이페이지 활동기록도
     * COMPLETED만 보면 후기 작성 이후 활동기록이 사라지는 결함이 생긴다.
     */
    List<PostingParticipation> findAllByUserIdAndStatusIn(
            Long userId, Collection<PostingParticipationStatus> statuses);

    /**
     * 상태 필터 없이 특정 사용자의 모든 참여 이력을 조회한다 — APPLIED/CONFIRMED/COMPLETED/REVIEWED를 전부 포함한다. 추천 후보에서 "이미
     * 지원한 적 있는 공고"를 제외하는 용도로만 사용한다. 진행 중인 참여만 필요하면 {@link #findByUserIdAndStatusNotIn}처럼 상태를 명시하는
     * 메서드를 사용할 것 — 이 메서드를 그런 용도로 재사용하지 않는다.
     */
    List<PostingParticipation> findByUserId(Long userId);
}
