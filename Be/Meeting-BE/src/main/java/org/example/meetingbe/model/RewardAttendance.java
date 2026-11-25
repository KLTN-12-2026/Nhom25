package org.example.meetingbe.model;
import jakarta.persistence.*;
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "meeting_id"}))
public class RewardAttendance {
@Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
public Long userId;
public Long meetingId;
public String meetingCode;
public java.time.Instant connectedAt;
public long seconds;
public boolean credited;
public java.time.LocalDate creditedDate;
}
