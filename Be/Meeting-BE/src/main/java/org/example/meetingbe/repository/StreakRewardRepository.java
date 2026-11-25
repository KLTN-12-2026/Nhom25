package org.example.meetingbe.repository;

import org.example.meetingbe.model.StreakReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StreakRewardRepository extends JpaRepository<StreakReward, Long> {
    List<StreakReward> findAllByOrderByStreakDaysAsc();
    Optional<StreakReward> findByStreakDays(Integer streakDays);
    boolean existsByStreakDays(Integer streakDays);
}