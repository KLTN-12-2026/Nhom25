package org.example.meetingbe.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.example.meetingbe.service.meetingAi.MeetingAiService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.github.benmanes.caffeine.cache.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/meeting")
public class MeetingAiController {
    private final IMeetingRepo meetings;
    private final IUserRepo users;
    private final IParticipantsRepo participants;
    private final MeetingAiService ai;
    private final Cache<Long, AtomicBoolean> requests = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterAccess(Duration.ofMinutes(5)).build();

    public MeetingAiController(IMeetingRepo meetings, IUserRepo users, IParticipantsRepo participants, MeetingAiService ai) {
        this.meetings = meetings; this.users = users; this.participants = participants; this.ai = ai;
    }
    public record Question(@NotBlank @Size(max = 2000) String question) {}
    public record Answer(String answer) {}

    @PostMapping("/rooms/{code}/ai-chat")
    public Answer ask(@PathVariable String code, @Valid @RequestBody Question body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        Meeting meeting = meetings.findByCode(code);
        if (meeting == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Phòng họp không tồn tại.");
        User user = users.findByUserName(auth.getName());
        Participants member = user == null ? null : participants.findByUserIdAndMeetingId(user.getId(), meeting.getId());
        if (member == null || member.getJoinAt() == null || member.getLeftAt() != null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn cần tham gia phòng họp để hỏi AI.");
        AtomicBoolean busy = requests.get(user.getId(), id -> new AtomicBoolean());
        if (!busy.compareAndSet(false, true)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Vui lòng chờ AI trả lời câu hỏi trước.");
        try { return new Answer(ai.answer(body.question().trim())); }
        finally { busy.set(false); }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                Objects.requireNonNullElse(e.getReason(), "Xử lý AI thất bại.")));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidQuestion() {
        return ResponseEntity.badRequest().body(Map.of("message", "Câu hỏi phải có từ 1 đến 2000 ký tự."));
    }
}
