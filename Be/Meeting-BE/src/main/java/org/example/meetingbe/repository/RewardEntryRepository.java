package org.example.meetingbe.repository;
import org.example.meetingbe.model.RewardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RewardEntryRepository extends JpaRepository<RewardEntry, Long> {
java.util.List<RewardEntry> findTop100ByUserIdOrderByIdDesc(Long userId);
}
