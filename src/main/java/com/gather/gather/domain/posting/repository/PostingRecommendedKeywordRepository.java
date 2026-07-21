package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingRecommendedKeyword;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingRecommendedKeywordRepository
        extends JpaRepository<PostingRecommendedKeyword, Long> {

    List<PostingRecommendedKeyword> findAllByOrderByScoreDesc();
}
