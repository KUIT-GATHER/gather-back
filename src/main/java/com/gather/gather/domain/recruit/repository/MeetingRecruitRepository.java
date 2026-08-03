package com.gather.gather.domain.recruit.repository;

import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRecruitRepository extends JpaRepository<MeetingRecruit, Long> {

    Optional<MeetingRecruit> findByPostId(Long postId);

    boolean existsByPostId(Long postId);
}
