package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.badge.service.BadgeEvaluationService;
import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostingParticipationService {

    private static final String VOLUNTEER_1365_APPLICATION_URL_PREFIX =
            "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=";

    /** V23 마이그레이션에서 정의한 (user_id, posting_id) 복합 유니크 제약. */
    private static final String PARTICIPATION_UNIQUE_CONSTRAINT =
            "uq_posting_participation_user_posting";

    private final PostingParticipationRepository postingParticipationRepository;
    private final PostingRepository postingRepository;
    private final BadgeEvaluationService badgeEvaluationService;

    @Transactional
    public PostingParticipationResponse apply(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        Posting posting =
                postingRepository
                        .findById(postingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        if (posting.getStatus() != PostingStatus.RECRUITING
                || !Boolean.TRUE.equals(posting.getIsActive())) {
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
            if (!isParticipationUniqueConstraintViolation(exception)) {
                throw exception;
            }
            log.warn("봉사 신청 저장 중 유니크 제약 위반. userId={}, postingId={}", userId, postingId, exception);
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE, exception);
        }

        return PostingParticipationResponse.of(
                participation.getId(),
                participation.getStatus(),
                VOLUNTEER_1365_APPLICATION_URL_PREFIX + posting.getExtId());
    }

    /** 개인 봉사 완료 판정: 활동종료일이 지난 뒤 본인이 직접 완료 처리한다(모임 봉사는 모임장이 별도로 완료 처리한다). */
    @Transactional
    public void complete(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        Posting posting =
                postingRepository
                        .findById(postingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        PostingParticipation participation =
                postingParticipationRepository
                        .findByUserIdAndPostingId(userId, postingId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        if (participation.getStatus() == PostingParticipationStatus.COMPLETED
                || participation.getStatus() == PostingParticipationStatus.REVIEWED) {
            throw new BusinessException(ErrorCode.PARTICIPATION_ALREADY_COMPLETED);
        }
        if (!posting.isActivityEnded(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARTICIPATION_COMPLETE_NOT_ALLOWED);
        }

        participation.complete();
        badgeEvaluationService.onVolunteerActivityCompleted(userId);
    }

    /** 완료 처리 이후 사용자가 직접 인정시간을 입력한다(분 단위, 1회만 입력 가능). */
    @Transactional
    public void submitRecognizedMinutes(Long postingId, Integer recognizedMinutes) {
        if (recognizedMinutes == null || recognizedMinutes <= 0 || recognizedMinutes % 10 != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Long userId = SecurityUtil.getCurrentUserId();
        PostingParticipation participation =
                postingParticipationRepository
                        .findByUserIdAndPostingId(userId, postingId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        if (participation.getStatus() != PostingParticipationStatus.COMPLETED
                && participation.getStatus() != PostingParticipationStatus.REVIEWED) {
            throw new BusinessException(ErrorCode.PARTICIPATION_HOURS_NOT_ALLOWED);
        }
        if (participation.getRecognizedMinutes() != null) {
            throw new BusinessException(ErrorCode.PARTICIPATION_HOURS_ALREADY_SUBMITTED);
        }

        participation.submitRecognizedMinutes(recognizedMinutes);
    }

    @Transactional
    public void cancel(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        PostingParticipation participation =
                postingParticipationRepository
                        .findByUserIdAndPostingId(userId, postingId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        // V23 마이그레이션 결정: 이력 보존을 위해 COMPLETED/REVIEWED 상태는 취소(삭제) 금지, APPLIED/CONFIRMED만 허용.
        if (participation.getStatus() == PostingParticipationStatus.COMPLETED
                || participation.getStatus() == PostingParticipationStatus.REVIEWED) {
            throw new BusinessException(ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);
        }

        postingParticipationRepository.delete(participation);
    }

    // 테이블에는 (user_id, posting_id) 유니크 제약 외에도 user/posting FK가 걸려 있어, 그 위반까지 전부
    // 중복 신청으로 오응답하지 않도록 실제 위반된 제약 이름을 확인한다.
    private boolean isParticipationUniqueConstraintViolation(
            DataIntegrityViolationException exception) {
        String constraintName = findConstraintName(exception);
        if (constraintName != null) {
            return constraintName
                    .replace("`", "")
                    .replace("\"", "")
                    .toLowerCase(Locale.ROOT)
                    .contains(PARTICIPATION_UNIQUE_CONSTRAINT);
        }
        return hasConstraintNameInMessage(exception);
    }

    private String findConstraintName(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && constraintViolationException.getConstraintName() != null) {
                return constraintViolationException.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private boolean hasConstraintNameInMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(PARTICIPATION_UNIQUE_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
