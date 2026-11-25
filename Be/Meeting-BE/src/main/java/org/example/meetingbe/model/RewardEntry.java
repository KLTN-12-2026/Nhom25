package org.example.meetingbe.model;
import jakarta.persistence.*;
@Entity
public class RewardEntry {
@Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
public Long userId;
public int points;
public String description;
public String meetingCode;
public java.time.Instant createdAt = java.time.Instant.now();
}
