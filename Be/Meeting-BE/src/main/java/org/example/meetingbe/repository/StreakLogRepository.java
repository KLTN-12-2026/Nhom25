package org.example.meetingbe.repository;

import org.example.meetingbe.model.StreakLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StreakLogRepository extends JpaRepository<StreakLog, Long> {
    boolean existsByUserIdAndCheckinDate(Long userId, LocalDate checkinDate);
    List<StreakLog> findAllByUserIdOrderByCheckinDateAsc(Long userId);
}