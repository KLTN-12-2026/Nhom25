package org.example.meetingbe.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "streak_logs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "checkin_date"})
})
public class StreakLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    public StreakLog() {}

    public StreakLog(User user, LocalDate checkinDate) {
        this.user = user;
        this.checkinDate = checkinDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
}