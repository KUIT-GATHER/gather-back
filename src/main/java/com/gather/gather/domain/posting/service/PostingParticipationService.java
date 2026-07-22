package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

        if (posting.getStatus() != PostingStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.POSTING_CLOSED);
        }

        if (posting.getExtId() == null) {
            throw new BusinessException(ErrorCode.POSTING_APPLICATION_UNAVAILABLE);
        }

        if (postingParticipationRepository.existsByUserIdAndPostingId(userId, postingId)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE);
        }

        PostingParticipation participation;
        // existsBy 사전 체크만으로는 동시 요청을 막지 못하므로, unique(user_id, posting_id) 제약 위반을
        // 최종 방어선으로 삼아 레이스 컨디션에서도 중복 신청을 막는다.
        try {
            participation =
                    postingParticipationRepository.saveAndFlush(
                            PostingParticipation.create(userId, postingId));
        } catch (DataIntegrityViolationException exception) {
            log.warn("봉사 신청 저장 중 유니크 제약 위반. userId={}, postingId={}", userId, postingId, exception);
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE, exception);
        }

        return PostingParticipationResponse.of(
                participation.getId(),
                participation.getStatus(),
                VOLUNTEER_1365_APPLICATION_URL_PREFIX + posting.getExtId());
    }

    @Transactional
    public void cancel(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        PostingParticipation participation =
                postingParticipationRepository
                        .findByUserIdAndPostingId(userId, postingId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        postingParticipationRepository.delete(participation);
    }
}
