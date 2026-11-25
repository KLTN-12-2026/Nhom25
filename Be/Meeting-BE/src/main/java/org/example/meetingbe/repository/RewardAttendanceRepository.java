package org.example.meetingbe.repository;
import org.example.meetingbe.model.RewardAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RewardAttendanceRepository extends JpaRepository<RewardAttendance, Long> {
java.util.Optional<RewardAttendance> findByUserIdAndMeetingId(Long userId, Long meetingId);
long countByMeetingId(Long meetingId);
long countByUserIdAndCreditedDate(Long userId, java.time.LocalDate date);
java.util.List<RewardAttendance> findByConnectedAtIsNotNull();
java.util.List<RewardAttendance> findTop30ByUserIdOrderByIdDesc(Long userId);
}
