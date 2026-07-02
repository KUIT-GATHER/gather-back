package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    Optional<Posting> findByExtId(String extId);

    boolean existsByExtId(String extId);
}
