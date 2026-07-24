package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingRecommendedKeyword;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRecommendedKeywordRepository
        extends JpaRepository<MeetingRecommendedKeyword, Long> {

    List<MeetingRecommendedKeyword> findAllByOrderByScoreDesc();
}
