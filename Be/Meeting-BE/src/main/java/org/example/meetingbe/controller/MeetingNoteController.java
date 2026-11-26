package org.example.meetingbe.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.meetingbe.model.ChatMessage;
import org.example.meetingbe.model.Meeting;
import org.example.meetingbe.service.chatMessage.ChatMessageService;
import org.example.meetingbe.service.meeting.MeetingDocument;
import org.example.meetingbe.model.MeetingNote;
import org.example.meetingbe.model.User;
import org.example.meetingbe.repository.IMeetingRepo;
import org.example.meetingbe.repository.IParticipantsRepo;
import org.example.meetingbe.repository.IUserRepo;
import org.example.meetingbe.repository.MeetingNoteRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/meetings/{roomCode}/note")
public class MeetingNoteController {
    private final IMeetingRepo meetings;
    private final IUserRepo users;
    private final IParticipantsRepo participants;
    private final MeetingNoteRepository notes;
    private final ChatMessageService chat;

    public MeetingNoteController(IMeetingRepo meetings, IUserRepo users,
                                 IParticipantsRepo participants, MeetingNoteRepository notes, ChatMessageService chat) {
        this.meetings = meetings;
        this.users = users;
        this.participants = participants;
        this.notes = notes;
        this.chat = chat;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Note read(@PathVariable String roomCode, Authentication auth) {
        User author = author(auth);
        Meeting meeting = meetings.findByCode(roomCode);
        requireAccess(meeting, author);
        return new Note(noteContent(meeting, author));
    }

    @GetMapping("/chat")
    @Transactional(readOnly = true)
    public List<ChatMessage> chatHistory(@PathVariable String roomCode, Authentication auth) {
        Meeting meeting = meetings.findByCode(roomCode);
        requireAccess(meeting, author(auth));
        return chat.getChatHistory(meeting.getCode());
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@PathVariable String roomCode,
            @RequestParam(defaultValue = "txt") String format, Authentication auth) throws IOException {
        User author = author(auth);
        Meeting meeting = meetings.findByCode(roomCode);
        requireAccess(meeting, author);

        String note = noteContent(meeting, author);
        StringBuilder text = new StringBuilder("CUỘC HỌP: ")
                .append(meeting.getTitle() == null ? meeting.getCode() : meeting.getTitle())
                .append("\r\nMã phòng: ").append(meeting.getCode())
                .append("\r\n\r\nGHI CHÚ CÁ NHÂN\r\n")
                .append(note.isBlank() ? "(Chưa có ghi chú)" : note)
                .append("\r\n\r\nLỊCH SỬ CHAT (giờ máy chủ)\r\n");
        List<ChatMessage> messages = chat.getChatHistory(meeting.getCode());
        if (messages.isEmpty()) {
            text.append("(Chưa có tin nhắn)\r\n");
        }
        DateTimeFormatter timeline = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        for (ChatMessage message : messages) {
            text.append('[').append(message.getTimestamp().format(timeline)).append("] ")
                    .append(message.getSenderId()).append(": ").append(message.getMessage())
                    .append("\r\n");
        }

        MediaType contentType;
        byte[] file;
        switch (format) {
            case "docx" -> {
                contentType = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                file = MeetingDocument.toDocx(text.toString());
            }
            case "txt" -> {
                contentType = new MediaType("text", "plain", StandardCharsets.UTF_8);
                file = ("\uFEFF" + text).getBytes(StandardCharsets.UTF_8);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng tệp không hợp lệ.");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("cuoc-hop-" + meeting.getId() + "." + format).build().toString())
                .contentType(contentType)
                .body(file);
    }

    private String noteContent(Meeting meeting, User author) {
        return notes.findByMeetingIdAndAuthorId(meeting.getId(), author.getId())
                .map(MeetingNote::getContent).orElse("");
    }

    @PutMapping
    @Transactional
    public Note save(@PathVariable String roomCode, @Valid @RequestBody Note request, Authentication auth) {
        User author = author(auth);
        // Serialize the first insert when the same note is saved from multiple tabs.
        Meeting meeting = meetings.findForUpdate(roomCode).orElse(null);
        requireAccess(meeting, author);
        MeetingNote note = notes.findByMeetingIdAndAuthorId(meeting.getId(), author.getId())
                .orElseGet(() -> new MeetingNote(meeting, author));
        note.setContent(request.content());
        notes.save(note);
        return request;
    }

    private User author(Authentication auth) {
        User user = auth == null ? null : users.findByUserName(auth.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    private void requireAccess(Meeting meeting, User author) {
        if (meeting == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (isUser(meeting.getUser(), author) || isUser(meeting.getCreator(), author)
                || participants.findByMeetingAndUser(meeting, author).isPresent()) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private boolean isUser(User candidate, User author) {
        return candidate != null && author.getId().equals(candidate.getId());
    }

    public record Note(@NotNull @Size(max = 10000) String content) {}
}
