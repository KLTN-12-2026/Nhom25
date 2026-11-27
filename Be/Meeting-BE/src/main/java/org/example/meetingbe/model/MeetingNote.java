package org.example.meetingbe.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(indexes = @Index(name = "idx_meeting_note_room_author", columnList = "meeting_id, author_id"))
public class MeetingNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 160)
    @ColumnDefault("''")
    private String title = "";

    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private long version;

    protected MeetingNote() {}

    public MeetingNote(Meeting meeting, User author) {
        this.meeting = meeting;
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public Long getId() {
        return id;
    }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }
}
