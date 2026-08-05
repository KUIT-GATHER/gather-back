package com.gather.gather.domain.mypage.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.mypage.dto.MyPageActivityRecordResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse.CategoryBlock;
import com.gather.gather.domain.mypage.dto.MyPageHomeResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.user.service.ProfileImageUrlResolver;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    /** 이 리포 전체가 COMPLETED/REVIEWED를 함께 "완료"로 취급한다(PostingParticipationAction 등과 동일 정책). */
    private static final Set<PostingParticipationStatus> COMPLETED_STATUSES =
            Set.of(PostingParticipationStatus.COMPLETED, PostingParticipationStatus.REVIEWED);

    private final UserRepository userRepository;
    private final BookmarkRepository bookmarkRepository;
    private final MeetingBookmarkRepository meetingBookmarkRepository;
    private final PostingParticipationRepository postingParticipationRepository;
    private final PostingRepository postingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
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

        List<MyPageActivityResponse> volunteerActivities =
                getVolunteerActivities(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        List<MyPageActivityResponse> meetingActivities = getMeetingActivities(userId, yearMonth);

        return Stream.concat(volunteerActivities.stream(), meetingActivities.stream())
                .sorted(Comparator.comparing(MyPageActivityResponse::actStartDate))
                .toList();
    }

    private List<MyPageActivityResponse> getVolunteerActivities(
            Long userId, LocalDate monthStart, LocalDate monthEnd) {
        List<PostingParticipation> participations =
                postingParticipationRepository.findByUserId(userId);
        if (participations.isEmpty()) {
            return List.of();
        }

        Map<Long, Posting> postingsById = fetchPostingsById(participations);

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
                .map(entry -> MyPageActivityResponse.ofVolunteer(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 승인된 멤버로 참여 중인 모임 중 활동기간이 조회 월과 겹치는 일정만 캘린더에 포함한다. */
    private List<MyPageActivityResponse> getMeetingActivities(Long userId, YearMonth yearMonth) {
        LocalDateTime monthStartInclusive = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEndExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<MeetingMember> approvedMembers =
                meetingMemberRepository.findApprovedForCalendar(
                        userId, monthStartInclusive, monthEndExclusive);

        Map<Long, PostingParticipationStatus> participationStatusByPostingId =
                fetchLinkedParticipationStatuses(userId, approvedMembers);

        return approvedMembers.stream()
                .map(
                        member ->
                                MyPageActivityResponse.ofMeeting(
                                        member.getMeeting(),
                                        resolveLinkedParticipationStatus(
                                                member.getMeeting(),
                                                participationStatusByPostingId)))
                .toList();
    }

    /**
     * {@code Map.of()}로 생성된 배치 조회 결과는 null 키 조회 시 NPE를 던지므로 자유 모임(volunteerPostingId=null)을 먼저
     * 걸러낸다.
     */
    private PostingParticipationStatus resolveLinkedParticipationStatus(
            Meeting meeting, Map<Long, PostingParticipationStatus> participationStatusByPostingId) {
        Long volunteerPostingId = meeting.getVolunteerPostingId();
        if (volunteerPostingId == null) {
            return null;
        }
        return participationStatusByPostingId.get(volunteerPostingId);
    }

    /**
     * 공고 기반 모임(volunteerPostingId != null)들의 연결 봉사공고에 대한 사용자의 참여 상태를 배치 조회한다. 모임과 참여 상태를 postingId
     * 하나로 매핑하는 이유: posting_participation에는 (user_id, posting_id) 유니크 제약이 있어 postingId당 참여가 최대 1건이다.
     */
    private Map<Long, PostingParticipationStatus> fetchLinkedParticipationStatuses(
            Long userId, List<MeetingMember> approvedMembers) {
        List<Long> volunteerPostingIds =
                approvedMembers.stream()
                        .map(member -> member.getMeeting().getVolunteerPostingId())
                        .filter(volunteerPostingId -> volunteerPostingId != null)
                        .distinct()
                        .toList();
        if (volunteerPostingIds.isEmpty()) {
            return Map.of();
        }

        return postingParticipationRepository
                .findAllByUserIdAndPostingIdIn(userId, volunteerPostingIds)
                .stream()
                .collect(
                        Collectors.toMap(
                                PostingParticipation::getPostingId,
                                PostingParticipation::getStatus));
    }

    /**
     * 활동기록 화면 - 활동 현황: 총 완료 횟수 + 분야별 블럭.
     *
     * <p>총 완료 횟수는 개인 봉사공고 참여와 모임 봉사를 합산한다. 분야별 블럭은 봉사공고 참여만 집계한다 — 모임은 최대 3개 분야를 동시에 가질 수 있어 단일 분야
     * 블럭에 귀속시킬 명확한 기준이 없다.
     */
    public MyPageActivitySummaryResponse getActivitySummary() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<PostingParticipation> completedParticipations = findCompletedParticipations(userId);
        List<Posting> resolvedPostings = resolvePostings(completedParticipations);
        List<MeetingMember> completedMeetingMembers = findCompletedMeetingMembers(userId);

        Map<PostingCategory, Long> countsByCategory =
                resolvedPostings.stream()
                        .collect(
                                Collectors.groupingBy(Posting::getCategory, Collectors.counting()));

        List<CategoryBlock> categoryBlocks =
                Arrays.stream(PostingCategory.values())
                        .map(
                                category ->
                                        new CategoryBlock(
                                                category,
                                                countsByCategory.getOrDefault(category, 0L)))
                        .toList();

        long totalCompletedCount = resolvedPostings.size() + completedMeetingMembers.size();
        long totalRecognizedMinutes =
                sumRecognizedMinutes(
                                completedParticipations, PostingParticipation::getRecognizedMinutes)
                        + sumRecognizedMinutes(
                                completedMeetingMembers, MeetingMember::getRecognizedMinutes);
        long timeCertifiableCompletedCount =
                countWithRecognizedMinutes(
                                completedParticipations, PostingParticipation::getRecognizedMinutes)
                        + countWithRecognizedMinutes(
                                completedMeetingMembers, MeetingMember::getRecognizedMinutes);

        return MyPageActivitySummaryResponse.of(
                totalCompletedCount,
                categoryBlocks,
                totalRecognizedMinutes,
                timeCertifiableCompletedCount);
    }

    /** 활동기록 상세의 봉사 카드 목록(봉사공고 참여 기준). category가 null이면 전체 분야를 반환하고, 최신 활동 시작일순으로 정렬한다. */
    public PageResponse<MyPageActivityRecordResponse> getActivityRecords(
            PostingCategory category, Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Long userId = SecurityUtil.getCurrentUserId();
        List<PostingParticipation> completed = findCompletedParticipations(userId);
        if (completed.isEmpty()) {
            return PageResponse.from(emptyPage(pageable));
        }

        Map<Long, Posting> postingsById = fetchPostingsById(completed);

        List<MyPageActivityRecordResponse> records =
                completed.stream()
                        .map(
                                participation ->
                                        Map.entry(
                                                participation,
                                                resolvePostingOrLog(participation, postingsById)))
                        .filter(entry -> entry.getValue() != null)
                        .filter(
                                entry ->
                                        category == null
                                                || entry.getValue().getCategory() == category)
                        .sorted(
                                Comparator.comparing(
                                                (Map.Entry<PostingParticipation, Posting> entry) ->
                                                        entry.getValue().getActStartDate(),
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        // 활동 시작일이 같은 카드가 여럿이면 순서가 조회마다 바뀌지 않도록 참여 ID로 타이브레이크한다.
                                        .thenComparing(
                                                (Map.Entry<PostingParticipation, Posting> entry) ->
                                                        entry.getKey().getId(),
                                                Comparator.reverseOrder()))
                        .map(
                                entry ->
                                        MyPageActivityRecordResponse.of(
                                                entry.getKey(), entry.getValue()))
                        .toList();

        return PageResponse.from(sliceInMemory(records, pageable));
    }

    /**
     * 완료된 참여 중 posting 조회에 성공한 것만 필터링한 목록 — totalCompletedCount와 categoryBlocks 합계가 항상 일치하도록 보장한다.
     */
    private List<Posting> resolvePostings(List<PostingParticipation> completedParticipations) {
        Map<Long, Posting> postingsById = fetchPostingsById(completedParticipations);
        return completedParticipations.stream()
                .map(participation -> resolvePostingOrLog(participation, postingsById))
                .filter(posting -> posting != null)
                .toList();
    }

    private List<MeetingMember> findCompletedMeetingMembers(Long userId) {
        return meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                userId, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED);
    }

    private <T> long sumRecognizedMinutes(List<T> items, Function<T, Integer> minutesExtractor) {
        return items.stream().mapToLong(item -> orZero(minutesExtractor.apply(item))).sum();
    }

    private <T> long countWithRecognizedMinutes(
            List<T> items, Function<T, Integer> minutesExtractor) {
        return items.stream().filter(item -> minutesExtractor.apply(item) != null).count();
    }

    private long orZero(Integer value) {
        return value == null ? 0L : value;
    }

    private List<PostingParticipation> findCompletedParticipations(Long userId) {
        return postingParticipationRepository.findAllByUserIdAndStatusIn(
                userId, COMPLETED_STATUSES);
    }

    private <T> Page<T> emptyPage(Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }

    private <T> Page<T> sliceInMemory(List<T> items, Pageable pageable) {
        long offset = pageable.getOffset();
        if (offset >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int start = (int) offset;
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    private Map<Long, Posting> fetchPostingsById(List<PostingParticipation> participations) {
        if (participations.isEmpty()) {
            return Map.of();
        }
        return postingRepository
                .findAllById(
                        participations.stream().map(PostingParticipation::getPostingId).toList())
                .stream()
                .collect(Collectors.toMap(Posting::getId, Function.identity()));
    }

    /**
     * posting_participation은 posting_id에 FK가 걸려 있어 정상 운영 중에는 항상 posting이 존재하지만, 참여한 공고를 찾지 못하는
     * 경우(데이터 정합성 이슈 등)를 대비해 방어적으로 로그를 남기고 결과에서 제외한다.
     */
    private Posting resolvePostingOrLog(
            PostingParticipation participation, Map<Long, Posting> postingsById) {
        Posting posting = postingsById.get(participation.getPostingId());
        if (posting == null) {
            log.warn(
                    "마이페이지 활동기록 조회 중 postingId={}에 해당하는 posting을 찾지 못함. participationId={}",
                    participation.getPostingId(),
                    participation.getId());
        }
        return posting;
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
