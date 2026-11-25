package org.example.meetingbe.model;
import jakarta.persistence.*;
@Entity
public class RewardClaim {
@Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
public Long userId;
public Long giftId;
public String giftName;
public int pointsSpent;
public java.time.Instant createdAt = java.time.Instant.now();
}
