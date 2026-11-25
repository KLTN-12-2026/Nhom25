package org.example.meetingbe.model;
import jakarta.persistence.*;
@Entity
public class RewardRules {
@Id public Long id = 1L;
public int minimumMinutes = 15;
public int meetingPoints = 10;
public int firstDailyBonus = 5;
public int dailyLimit = 3;
public int firstMilestone = 10;
public int firstMilestonePoints = 30;
public int secondMilestone = 30;
public int secondMilestonePoints = 100;
}
