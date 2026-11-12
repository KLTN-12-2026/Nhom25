package org.example.meetingbe.repository;

import org.example.meetingbe.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface IMeetingRepo extends JpaRepository<Meeting, Long> {

    Meeting findByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.code = :code")
    Optional<Meeting> findForUpdate(@Param("code") String code);
}
