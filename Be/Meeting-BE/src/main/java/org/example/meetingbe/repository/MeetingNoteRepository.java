package org.example.meetingbe.repository;

import org.example.meetingbe.model.MeetingNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingNoteRepository extends JpaRepository<MeetingNote, Long> {
    default Optional<MeetingNote> findByMeetingIdAndAuthorId(Long meetingId, Long authorId) {
        return findFirstByMeetingIdAndAuthorIdOrderByIdAsc(meetingId, authorId);
    }

    Optional<MeetingNote> findFirstByMeetingIdAndAuthorIdOrderByIdAsc(Long meetingId, Long authorId);
    Optional<MeetingNote> findByIdAndMeetingIdAndAuthorId(Long id, Long meetingId, Long authorId);
    List<MeetingNote> findAllByMeetingIdAndAuthorIdOrderByIdAsc(Long meetingId, Long authorId);
}
