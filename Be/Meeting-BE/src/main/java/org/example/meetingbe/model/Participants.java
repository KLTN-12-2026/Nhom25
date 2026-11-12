package org.example.meetingbe.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Participants {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "join_at", columnDefinition = "DATETIME")
    private LocalDateTime joinAt;
    @Column(name = "left_at", columnDefinition = "DATETIME")
    private LocalDateTime leftAt;
    @ManyToOne
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    @Column(name = "removed_from_meeting")
    private Boolean removed;

    public boolean isRemoved() { return Boolean.TRUE.equals(removed); }
    public void setRemoved(boolean removed) { this.removed = removed; }

    public Participants() {
    }

    public Participants(LocalDateTime joinAt, LocalDateTime leftAt, Meeting meeting, User user) {
        this.joinAt = joinAt;
        this.leftAt = leftAt;
        this.meeting = meeting;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getJoinAt() {
        return joinAt;
    }

    public void setJoinAt(LocalDateTime joinAt) {
        this.joinAt = joinAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(LocalDateTime leftAt) {
        this.leftAt = leftAt;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
