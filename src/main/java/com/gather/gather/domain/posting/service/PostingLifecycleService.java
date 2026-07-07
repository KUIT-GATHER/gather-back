package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostingLifecycleService {

    private final PostingRepository postingRepository;

    @Transactional
    public int deactivateExpiredPostings() {
        return postingRepository.deactivateExpired(LocalDate.now(), LocalDateTime.now());
    }
}
