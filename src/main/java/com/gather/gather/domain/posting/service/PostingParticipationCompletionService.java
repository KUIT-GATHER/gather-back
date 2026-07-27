package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고 활동종료일이 지난 참여를 COMPLETED로 일괄 전이한다(devplan2 8-2③).
 *
 * <p>담당자 확인 단계(CONFIRMED)를 판정할 UI/데이터가 없어 APPLIED/CONFIRMED 모두 COMPLETED로 직행시키는 임시 규칙이며, 실제 요구사항과
 * 다를 수 있어 구현 착수 전 재확인이 필요하다고 문서에 남겨뒀다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingParticipationCompletionService {

    private static final EnumSet<PostingParticipationStatus> COMPLETABLE_STATUSES =
            EnumSet.of(PostingParticipationStatus.APPLIED, PostingParticipationStatus.CONFIRMED);

    private final PostingParticipationRepository postingParticipationRepository;
    private final PostingRepository postingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int completeExpiredParticipations() {
        List<PostingParticipation> candidates =
                postingParticipationRepository.findByStatusIn(COMPLETABLE_STATUSES);
        if (candidates.isEmpty()) {
            return 0;
        }

        Map<Long, Posting> postingsById =
                postingRepository
                        .findAllById(
                                candidates.stream()
                                        .map(PostingParticipation::getPostingId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(Posting::getId, Function.identity()));

        LocalDate today = LocalDate.now();
        int count = 0;
        for (PostingParticipation participation : candidates) {
            Posting posting = postingsById.get(participation.getPostingId());
            if (isExpired(posting, today)) {
                participation.complete();
                eventPublisher.publishEvent(
                        new PostingParticipationCompletedEvent(
                                participation.getUserId(), participation.getPostingId()));
                count++;
            }
        }
        return count;
    }

    /**
     * posting_participation은 posting_id FK가 걸려 있어 정상 운영 중엔 항상 posting이 존재한다(MyPageService와 동일 전제).
     */
    private boolean isExpired(Posting posting, LocalDate today) {
        if (posting == null) {
            log.warn("참여 완료 배치 중 posting을 찾지 못해 건너뜀");
            return false;
        }
        LocalDate effectiveEndDate =
                posting.getActEndDate() != null
                        ? posting.getActEndDate()
                        : posting.getActStartDate();
        return effectiveEndDate != null && effectiveEndDate.isBefore(today);
    }
}
