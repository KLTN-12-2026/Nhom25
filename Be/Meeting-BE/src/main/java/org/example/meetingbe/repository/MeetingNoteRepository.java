package org.example.meetingbe.repository;

import org.example.meetingbe.model.MeetingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MeetingNoteRepository extends JpaRepository<MeetingNote, Long> {
    Optional<MeetingNote> findFirstByMeetingIdAndAuthorIdOrderByIdAsc(Long meetingId, Long authorId);
    Optional<MeetingNote> findByIdAndMeetingIdAndAuthorId(Long id, Long meetingId, Long authorId);
    List<MeetingNote> findAllByMeetingIdAndAuthorIdOrderByIdAsc(Long meetingId, Long authorId);

    interface HistoryNote {
        String getTitle();
        String getContent();
    }

    @Query("""
            select m.title as title, n.content as content
            from MeetingNote n join n.meeting m
            where n.author.id = :authorId and n.id <> :excludedNoteId
            order by n.updatedAt desc, n.id desc
            """)
    List<HistoryNote> findPersonalHistory(Long authorId, Long excludedNoteId);
}
