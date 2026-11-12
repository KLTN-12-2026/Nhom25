package org.example.meetingbe.repository;

import org.example.meetingbe.model.MeetingRoleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IMeetingRoleHistoryRepo extends JpaRepository<MeetingRoleHistory, Long> {}
