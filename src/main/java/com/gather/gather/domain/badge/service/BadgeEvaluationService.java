package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 봉사공고 완료(PostingParticipation)와 모임 봉사 완료(MeetingMember)를 합산해 판정하는 뱃지 트리거.
 *
 * <p>완료 시점은 각 엔티티의 updatedAt(개인 완료 처리 시점 / 모임 완료 처리 시점)을 기준으로 삼는다.
 */
@Service
@RequiredArgsConstructor
public class BadgeEvaluationService {

    private static final int COMPLETION_5_THRESHOLD = 5;
    private static final int CONSECUTIVE_MONTHS_THRESHOLD = 3;

    private final PostingParticipationRepository postingParticipationRepository;
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
        for (PostingParticipation participation :
                postingParticipationRepository.findAllByUserIdAndStatus(
                        userId, PostingParticipationStatus.COMPLETED)) {
            dates.add(participation.getUpdatedAt().toLocalDate());
        }
        for (MeetingMember member :
                meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                        userId, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED)) {
            dates.add(member.getMeeting().getUpdatedAt().toLocalDate());
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
