package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 봉사공고 완료(PostingParticipation)와 모임 봉사 완료(MeetingMember)를 합산해 판정하는 뱃지 트리거.
 *
 * <p>판정 기준일은 실제 활동일이다 — 개인 봉사는 공고의 실질 활동일(actEndDate, 없으면 activityDate), 모임 봉사는 모임의 실질 활동 종료
 * 시각(activityEndAt, 없으면 activityStartAt)을 사용한다. "완료 버튼을 누른 시각"(completedAt)을 기준으로 삼으면 여러 달에 걸쳐 실제
 * 활동한 봉사를 한 번에 몰아 완료 처리했을 때 연속봉사 판정이 실제 활동 이력과 어긋난다. 활동 기간이 아예 없는 자유 모임만 completedAt으로 대체한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeEvaluationService {

    private static final int COMPLETION_5_THRESHOLD = 5;
    private static final int CONSECUTIVE_MONTHS_THRESHOLD = 3;

    /** 이 리포 전체가 COMPLETED/REVIEWED를 함께 "완료"로 취급한다(PostingParticipationAction 등과 동일 정책). */
    private static final Set<PostingParticipationStatus> COMPLETED_STATUSES =
            Set.of(PostingParticipationStatus.COMPLETED, PostingParticipationStatus.REVIEWED);

    private final PostingParticipationRepository postingParticipationRepository;
    private final PostingRepository postingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final BadgeAwardService badgeAwardService;

    @Transactional
    public void onVolunteerActivityCompleted(Long userId) {
        List<LocalDate> completionDates = collectCompletionDates(userId);

        if (!completionDates.isEmpty()) {
            badgeAwardService.award(userId, BadgeType.FIRST_COMPLETION);
        }
        if (completionDates.size() >= COMPLETION_5_THRESHOLD) {
            badgeAwardService.award(userId, BadgeType.COMPLETION_5);
        }
        if (hasConsecutiveMonths(completionDates, CONSECUTIVE_MONTHS_THRESHOLD)) {
            badgeAwardService.award(userId, BadgeType.CONSECUTIVE_3_MONTHS);
        }
    }

    private List<LocalDate> collectCompletionDates(Long userId) {
        List<LocalDate> dates = new ArrayList<>();
        dates.addAll(collectPostingActivityDates(userId));
        dates.addAll(collectMeetingActivityDates(userId));
        return dates;
    }

    private List<LocalDate> collectPostingActivityDates(Long userId) {
        List<PostingParticipation> participations =
                postingParticipationRepository.findAllByUserIdAndStatusIn(
                        userId, COMPLETED_STATUSES);
        if (participations.isEmpty()) {
            return List.of();
        }

        Map<Long, Posting> postingsById =
                postingRepository
                        .findAllById(
                                participations.stream()
                                        .map(PostingParticipation::getPostingId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(Posting::getId, Function.identity()));

        List<LocalDate> dates = new ArrayList<>();
        for (PostingParticipation participation : participations) {
            Posting posting = postingsById.get(participation.getPostingId());
            if (posting == null) {
                log.warn(
                        "뱃지 판정 중 postingId={}에 해당하는 posting을 찾지 못해 스킵. participationId={}",
                        participation.getPostingId(),
                        participation.getId());
                continue;
            }
            dates.add(posting.getEffectiveActivityDate());
        }
        return dates;
    }

    private List<LocalDate> collectMeetingActivityDates(Long userId) {
        List<LocalDate> dates = new ArrayList<>();
        for (MeetingMember member :
                meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                        userId, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED)) {
            Meeting meeting = member.getMeeting();
            LocalDateTime activityEnd = meeting.getEffectiveActivityEnd();
            if (activityEnd != null) {
                dates.add(activityEnd.toLocalDate());
                continue;
            }
            // 활동 기간이 없는 자유 모임은 완료 처리 시각으로 대체한다.
            if (meeting.getCompletedAt() == null) {
                log.warn(
                        "뱃지 판정 중 활동기간·completedAt이 모두 없는 모임 스킵. meetingId={}, userId={}",
                        meeting.getId(),
                        userId);
                continue;
            }
            dates.add(meeting.getCompletedAt().toLocalDate());
        }
        return dates;
    }

    private boolean hasConsecutiveMonths(List<LocalDate> dates, int threshold) {
        TreeSet<YearMonth> months = new TreeSet<>();
        for (LocalDate date : dates) {
            months.add(YearMonth.from(date));
        }

        int streak = 0;
        YearMonth previous = null;
        for (YearMonth month : months) {
            streak = (previous != null && previous.plusMonths(1).equals(month)) ? streak + 1 : 1;
            if (streak >= threshold) {
                return true;
            }
            previous = month;
        }
        return false;
    }
}
