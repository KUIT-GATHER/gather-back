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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
                .filter(
                        entry ->
                                isVisibleInMonth(
                                        entry.getKey(), entry.getValue(), monthStart, monthEnd))
                .sorted(Comparator.comparing(entry -> entry.getValue().getActStartDate()))
                .map(entry -> MyPageActivityResponse.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * posting_participation은 posting_id에 FK가 걸려 있어 정상 운영 중에는 항상 posting이 존재하지만, 참여한 공고를 찾지 못하는
     * 경우(데이터 정합성 이슈 등)를 대비해 방어적으로 로그를 남기고 캘린더에서 제외한다.
     */
    private boolean isVisibleInMonth(
            PostingParticipation participation,
            Posting posting,
            LocalDate monthStart,
            LocalDate monthEnd) {
        if (posting == null) {
            log.warn(
                    "마이페이지 활동 조회 중 postingId={}에 해당하는 posting을 찾지 못함. participationId={}",
                    participation.getPostingId(),
                    participation.getId());
            return false;
        }
        return isWithinMonth(
                posting.getActStartDate(), posting.getActEndDate(), monthStart, monthEnd);
    }

    /** 종료일이 없는 단일 일정은 시작일과 동일한 것으로 간주하고, 활동 기간과 조회 월의 겹침 여부로 판단한다. */
    private boolean isWithinMonth(
            LocalDate actStartDate,
            LocalDate actEndDate,
            LocalDate monthStart,
            LocalDate monthEnd) {
        if (actStartDate == null) {
            return false;
        }
        LocalDate effectiveEndDate = actEndDate != null ? actEndDate : actStartDate;
        return !actStartDate.isAfter(monthEnd) && !effectiveEndDate.isBefore(monthStart);
    }
}
