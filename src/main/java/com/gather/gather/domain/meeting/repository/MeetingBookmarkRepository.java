package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.dto.BookmarkedMeetingDeadlineTarget;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingBookmarkRepository extends JpaRepository<MeetingBookmark, Long> {

    boolean existsByUserIdAndMeetingId(Long userId, Long meetingId);

    boolean existsByUserId(Long userId);

    Optional<MeetingBookmark> findByUserIdAndMeetingId(Long userId, Long meetingId);

    @Modifying
    @Query(
            "DELETE FROM MeetingBookmark b WHERE b.userId = :userId AND b.meetingId ="
                    + " :meetingId")
    int deleteByUserIdAndMeetingId(
            @Param("userId") Long userId, @Param("meetingId") Long meetingId);

    /**
     * MeetingBookmark는 Meeting과 연관관계 없이 FK id만 보관하므로 명시적 ON 절로 조인한다.
     *
     * <p>activityStartAt/activityEndAt은 MeetingRepository.searchMeetings와 동일하게 활동기간 겹침 필터다(모임 목록
     * 조회와 동일 정책).
     */
    @Query(
            """
            select m from Meeting m
            join MeetingBookmark b on b.meetingId = m.id
            where b.userId = :userId
              and m.deletedAt is null
              and (:category is null or :category member of m.categories)
              and (:keyword is null
                   or m.name like concat('%', :keyword, '%') escape '\\'
                   or m.description like concat('%', :keyword, '%') escape '\\')
              and (:hasRegionFilter = false or m.regionId in :regionIds)
              and (:activityStartAt is null or m.activityEndAt >= :activityStartAt)
              and (:activityEndAt is null or m.activityStartAt <= :activityEndAt)
            order by b.createdAt desc, b.id desc
            """)
    Page<Meeting> findBookmarkedMeetings(
            @Param("userId") Long userId,
            @Param("category") PostingCategory category,
            @Param("keyword") String keyword,
            @Param("hasRegionFilter") boolean hasRegionFilter,
            @Param("regionIds") List<Long> regionIds,
            @Param("activityStartAt") LocalDateTime activityStartAt,
            @Param("activityEndAt") LocalDateTime activityEndAt,
            Pageable pageable);

    @Query(
            """
            SELECT new com.gather.gather.domain.meeting.dto.BookmarkedMeetingDeadlineTarget(
                bookmark.userId,
                meeting.id,
                meeting.name
            )
            FROM MeetingBookmark bookmark
            JOIN Meeting meeting ON meeting.id = bookmark.meetingId
            JOIN User user ON user.id = bookmark.userId
            WHERE meeting.deadline >= :deadlineStartInclusive
              AND meeting.deadline < :deadlineEndExclusive
              AND meeting.status =
                  com.gather.gather.domain.meeting.enums.MeetingStatus.RECRUITING
              AND meeting.deletedAt IS NULL
              AND meeting.currentMemberCount < meeting.maxMember
              AND user.status =
                  com.gather.gather.domain.auth.entity.UserStatus.ACTIVE
            """)
    List<BookmarkedMeetingDeadlineTarget> findMeetingDeadlineNotificationTargets(
            @Param("deadlineStartInclusive") LocalDateTime deadlineStartInclusive,
            @Param("deadlineEndExclusive") LocalDateTime deadlineEndExclusive);
}
