package com.gather.gather.domain.meeting.entity;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
@Entity
@Getter
@Table(
        name = "meeting_member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_meeting_member_user_meeting",
                        columnNames = {"user_id", "meeting_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingMemberRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingMemberStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    private MeetingMember(
            MeetingMemberRole role,
            User user,
            Meeting meeting,
            MeetingMemberStatus status) {
        this.role = role;
        this.user = user;
        this.meeting = meeting;
        this.status = status;
        this.joinedAt = LocalDateTime.now();
    }

    public static MeetingMember createHost(User user, Meeting meeting) {
        return new MeetingMember(
                MeetingMemberRole.HOST,
                user,
                meeting,
                MeetingMemberStatus.APPROVED);
    }
    public static MeetingMember createMember(User user, Meeting meeting) {
        return new MeetingMember(
                MeetingMemberRole.MEMBER,
                user,
                meeting,
                MeetingMemberStatus.APPROVED);
    }

    public void leave() {
        this.status = MeetingMemberStatus.LEFT;
    }
}