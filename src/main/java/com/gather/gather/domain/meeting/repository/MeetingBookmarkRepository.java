package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import com.gather.gather.domain.posting.entity.PostingCategory;
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

    /** 회원 탈퇴 시 북마크를 전량 정리한다. */
    @Modifying
    @Query("DELETE FROM MeetingBookmark b WHERE b.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /** MeetingBookmark는 Meeting과 연관관계 없이 FK id만 보관하므로 명시적 ON 절로 조인한다. */
    @Query(
            """
            select m from Meeting m
            join MeetingBookmark b on b.meetingId = m.id
            where b.userId = :userId
              and m.deletedAt is null
              and (:category is null or m.category = :category)
              and (:keyword is null
                   or m.name like concat('%', :keyword, '%') escape '\\'
                   or m.description like concat('%', :keyword, '%') escape '\\')
            order by b.createdAt desc, b.id desc
            """)
    Page<Meeting> findBookmarkedMeetings(
            @Param("userId") Long userId,
            @Param("category") PostingCategory category,
            @Param("keyword") String keyword,
            Pageable pageable);
}
