package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostingParticipationService {

    private static final String VOLUNTEER_1365_APPLICATION_URL_PREFIX =
            "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=";

    private final PostingParticipationRepository postingParticipationRepository;
    private final PostingRepository postingRepository;

    @Transactional
    public PostingParticipationResponse apply(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        Posting posting =
                postingRepository
                        .findById(postingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        if (postingParticipationRepository.existsByUserIdAndPostingId(userId, postingId)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE);
        }

        PostingParticipation participation;
        try {
            participation =
                    postingParticipationRepository.saveAndFlush(
                            PostingParticipation.create(userId, postingId));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE);
        }

        return PostingParticipationResponse.of(
                participation.getId(),
                participation.getStatus(),
                VOLUNTEER_1365_APPLICATION_URL_PREFIX + posting.getExtId());
    }
}
