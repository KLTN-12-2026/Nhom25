package org.example.meetingbe.model;
import jakarta.persistence.*;
@Entity
public class RewardAccount {
@Id public Long userId;
public int balance;
public int lifetimePoints;
public int meetingCount;
public boolean firstMilestoneClaimed;
public boolean secondMilestoneClaimed;
}
