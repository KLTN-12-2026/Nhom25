package org.example.meetingbe.repository;

import org.example.meetingbe.model.MeetingNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetingNoteRepository extends JpaRepository<MeetingNote, Long> {
    Optional<MeetingNote> findByMeetingIdAndAuthorId(Long meetingId, Long authorId);
}
