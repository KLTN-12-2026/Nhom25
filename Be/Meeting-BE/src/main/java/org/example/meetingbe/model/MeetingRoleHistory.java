package org.example.meetingbe.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_role_history")
public class MeetingRoleHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    private Long actorId;
    private String action;
    private Long previousHostId;
    private Long newHostId;
    private Long previousCoHostId;
    private Long newCoHostId;
    private LocalDateTime createdAt;

    public MeetingRoleHistory() {}

    public MeetingRoleHistory(Meeting meeting, Long actorId, String action,
                              Long previousHostId, Long previousCoHostId) {
        this.meeting = meeting;
        this.actorId = actorId;
        this.action = action;
        this.previousHostId = previousHostId;
        this.previousCoHostId = previousCoHostId;
        this.newHostId = meeting.getUser().getId();
        this.newCoHostId = meeting.getCoHost() == null ? null : meeting.getCoHost().getId();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public Long getPreviousHostId() { return previousHostId; }
    public Long getNewHostId() { return newHostId; }
    public Long getPreviousCoHostId() { return previousCoHostId; }
    public Long getNewCoHostId() { return newCoHostId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
