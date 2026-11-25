package org.example.meetingbe.repository;
import org.example.meetingbe.model.RewardClaim;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RewardClaimRepository extends JpaRepository<RewardClaim, Long> {
java.util.List<RewardClaim> findTop100ByUserIdOrderByIdDesc(Long userId);
boolean existsByUserIdAndGiftId(Long userId, Long giftId);
}
