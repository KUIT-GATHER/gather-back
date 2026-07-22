package com.gather.gather.domain.mypage.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
import com.gather.gather.domain.mypage.dto.MyPageHomeResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.user.service.ProfileImageUrlResolver;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    /** 캘린더에는 아직 진행 중인 참여만 노출한다(문서 2-2절: COMPLETED/REVIEWED 이전 상태만). */
    private static final Set<PostingParticipationStatus> CALENDAR_EXCLUDED_STATUSES =
            Set.of(PostingParticipationStatus.COMPLETED, PostingParticipationStatus.REVIEWED);

    private final UserRepository userRepository;
    private final BookmarkRepository bookmarkRepository;
    private final MeetingBookmarkRepository meetingBookmarkRepository;
    private final PostingParticipationRepository postingParticipationRepository;
    private final PostingRepository postingRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    public MyPageHomeResponse getHome() {
        Long userId = SecurityUtil.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean hasBookmark =
                bookmarkRepository.existsByUserId(userId)
                        || meetingBookmarkRepository.existsByUserId(userId);

        return MyPageHomeResponse.of(
                user, profileImageUrlResolver.resolve(user.getProfileImageKey()), hasBookmark);
    }

    public List<MyPageActivityResponse> getActivities(YearMonth yearMonth) {
        Long userId = SecurityUtil.getCurrentUserId();

        List<PostingParticipation> participations =
                postingParticipationRepository.findByUserIdAndStatusNotIn(
                        userId, CALENDAR_EXCLUDED_STATUSES);
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

        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        return participations.stream()
                .map(
                        participation ->
                                Map.entry(
                                        participation,
                                        postingsById.get(participation.getPostingId())))
                .filter(entry -> entry.getValue() != null)
                .filter(
                        entry ->
                                isWithinMonth(
                                        entry.getValue().getActStartDate(), monthStart, monthEnd))
                .sorted(Comparator.comparing(entry -> entry.getValue().getActStartDate()))
                .map(entry -> MyPageActivityResponse.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean isWithinMonth(
            LocalDate actStartDate, LocalDate monthStart, LocalDate monthEnd) {
        return actStartDate != null
                && !actStartDate.isBefore(monthStart)
                && !actStartDate.isAfter(monthEnd);
    }
}
