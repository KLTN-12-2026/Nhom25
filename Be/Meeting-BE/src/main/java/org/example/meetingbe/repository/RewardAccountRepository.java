package org.example.meetingbe.repository;
import org.example.meetingbe.model.RewardAccount;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RewardAccountRepository extends JpaRepository<RewardAccount, Long> {
@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
@org.springframework.data.jpa.repository.Query("select a from RewardAccount a where a.userId = :id")
java.util.Optional<RewardAccount> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
