package com.gather.gather.domain.mypage.service;

import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 특정 사용자의 누적 인정 시간(분)을 세 출처(봉사공고 참여, 모임 완료, 모집공고 출석)에서 합산한다. 가입 신청 상세·멤버 상세·신청자 상세의 {@code
 * totalRecognizedMinutes}에 공통으로 쓰인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRecognizedMinutesService {

    private final PostingParticipationRepository postingParticipationRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final MeetingRecruitParticipationRepository meetingRecruitParticipationRepository;

    public int getTotalRecognizedMinutes(Long userId) {
        int postingMinutes = postingParticipationRepository.sumRecognizedMinutesByUserId(userId);
        int meetingMinutes = meetingMemberRepository.sumRecognizedMinutesByUserId(userId);
        int recruitMinutes =
                meetingRecruitParticipationRepository.sumRecognizedMinutesByUserId(userId);
        return postingMinutes + meetingMinutes + recruitMinutes;
    }
}
