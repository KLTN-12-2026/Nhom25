package org.example.meetingbe.model;
import jakarta.persistence.*;
@Entity
public class RewardGift {
@Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
public String name;
public String description;
public String type = "VIP";
public int cost;
public int vipDays;
public boolean active = true;
}
