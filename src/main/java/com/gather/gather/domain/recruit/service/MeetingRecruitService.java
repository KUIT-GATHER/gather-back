package com.gather.gather.domain.recruit.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.recruit.dto.RecruitCreateRequest;
import com.gather.gather.domain.recruit.dto.RecruitDetailResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipationResponse;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.domain.recruit.repository.MeetingRecruitRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 내부 모집공고(RECRUIT) 작성·상세 및 참여신청.
 *
 * <p>권한 정책
 *
 * <ul>
 *   <li>작성: 모임장(HOST)만
 *   <li>상세 열람: 가입자만(RECRUIT은 미가입자 비노출)
 *   <li>참여신청/취소: 가입자만, 신청 마감일 이전에만, 정원 내에서만
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingRecruitService {

    private final PostRepository postRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;
    private final MeetingRecruitRepository meetingRecruitRepository;
    private final MeetingRecruitParticipationRepository participationRepository;

    @Transactional
    public RecruitDetailResponse createRecruit(Long meetingId, RecruitCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeeting(meetingId);
        MeetingMember membership = getApprovedMembership(meetingId, userId);
        if (membership.getRole() != MeetingMemberRole.HOST) {
            throw new BusinessException(ErrorCode.RECRUIT_HOST_ONLY);
        }
        if (request.timeRecognized() && request.recognizedMinutes() == null) {
            throw new BusinessException(ErrorCode.RECRUIT_RECOGNIZED_MINUTES_REQUIRED);
        }

        User author = getUser(userId);
        Post post =
                postRepository.save(
                        Post.create(
                                meeting,
                                author,
                                request.title(),
                                request.content(),
                                PostType.RECRUIT,
                                request.maxParticipants()));

        MeetingRecruit recruit =
                meetingRecruitRepository.save(
                        MeetingRecruit.create(
                                post.getId(),
                                request.place(),
                                request.actDate(),
                                request.actStartTime(),
                                request.actEndTime(),
                                request.maxParticipants(),
                                request.timeRecognized(),
                                request.recognizedMinutes(),
                                request.applyDeadline(),
                                request.isExternal(),
                                request.categories()));

        return toDetail(post, recruit, author, 0, false, true, true);
    }

    public RecruitDetailResponse getRecruit(Long meetingId, Long postId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);
        if (!isApprovedMember(meetingId, userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        Post post = getRecruitPost(meetingId, postId);
        MeetingRecruit recruit = getRecruitDetail(postId);

        int appliedCount = (int) participationRepository.countByPostId(postId);
        boolean applied = participationRepository.existsByPostIdAndUserId(postId, userId);
        boolean host = isHost(meetingId, userId);
        return toDetail(
                post,
                recruit,
                post.getUser(),
                appliedCount,
                applied,
                post.isAuthor(userId),
                post.isAuthor(userId) || host);
    }

    @Transactional
    public RecruitParticipationResponse toggleParticipation(Long meetingId, Long postId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);
        if (!isApprovedMember(meetingId, userId)) {
            throw new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED);
        }

        getRecruitPost(meetingId, postId);
        MeetingRecruit recruit = getRecruitDetail(postId);

        // 신청기간이 끝나면 신청·취소 모두 불가(피그마: 종료 후 버튼 상태 변경 불가).
        if (!recruit.isApplicationOpen(LocalDate.now())) {
            throw new BusinessException(ErrorCode.RECRUIT_APPLICATION_CLOSED);
        }

        long currentCount = participationRepository.countByPostId(postId);
        Optional<MeetingRecruitParticipation> existing =
                participationRepository.findByPostIdAndUserId(postId, userId);

        if (existing.isPresent()) {
            participationRepository.delete(existing.get());
            return new RecruitParticipationResponse(
                    false, (int) (currentCount - 1), recruit.getMaxParticipants());
        }

        if (currentCount >= recruit.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.RECRUIT_CAPACITY_EXCEEDED);
        }
        participationRepository.save(MeetingRecruitParticipation.apply(postId, userId));
        return new RecruitParticipationResponse(
                true, (int) (currentCount + 1), recruit.getMaxParticipants());
    }

    private RecruitDetailResponse toDetail(
            Post post,
            MeetingRecruit recruit,
            User author,
            int appliedCount,
            boolean applied,
            boolean canEdit,
            boolean canDelete) {
        return new RecruitDetailResponse(
                post.getId(),
                post.getMeeting().getId(),
                post.getTitle(),
                post.getContent(),
                author.getId(),
                author.getNickname(),
                recruit.getPlace(),
                recruit.getActDate(),
                recruit.getActStartTime(),
                recruit.getActEndTime(),
                recruit.getMaxParticipants(),
                recruit.getCategories(),
                recruit.isTimeRecognized(),
                recruit.getRecognizedMinutes(),
                recruit.getApplyDeadline(),
                recruit.isExternal(),
                post.getLikeCount(),
                post.getCommentCount(),
                appliedCount,
                applied,
                recruit.isApplicationOpen(LocalDate.now()),
                appliedCount >= recruit.getMaxParticipants(),
                canEdit,
                canDelete,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private Meeting getMeeting(Long meetingId) {
        return meetingRepository
                .findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    /** RECRUIT 유형이면서 해당 모임에 속한 게시글을 조회한다. */
    private Post getRecruitPost(Long meetingId, Long postId) {
        Post post =
                postRepository
                        .findByIdFetchUser(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_NOT_FOUND));
        if (!post.getMeeting().getId().equals(meetingId) || post.getType() != PostType.RECRUIT) {
            throw new BusinessException(ErrorCode.RECRUIT_NOT_FOUND);
        }
        return post;
    }

    private MeetingRecruit getRecruitDetail(Long postId) {
        return meetingRecruitRepository
                .findByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean isApprovedMember(Long meetingId, Long userId) {
        return meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                meetingId, userId, MeetingMemberStatus.APPROVED);
    }

    private MeetingMember getApprovedMembership(Long meetingId, Long userId) {
        return meetingMemberRepository
                .findByMeeting_IdAndUser_IdAndStatus(
                        meetingId, userId, MeetingMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED));
    }

    private boolean isHost(Long meetingId, Long userId) {
        return meetingMemberRepository
                .findByMeeting_IdAndUser_IdAndStatus(
                        meetingId, userId, MeetingMemberStatus.APPROVED)
                .map(member -> member.getRole() == MeetingMemberRole.HOST)
                .orElse(false);
    }
}
