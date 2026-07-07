package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingLocationRepository extends JpaRepository<PostingLocation, Long> {

    List<PostingLocation> findAllByPostingIdOrderByLocationSeq(Long postingId);
}
