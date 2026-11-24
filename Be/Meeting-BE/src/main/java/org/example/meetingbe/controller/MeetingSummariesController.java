package org.example.meetingbe.controller;

import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.example.meetingbe.service.meetingSummaries.GeminiMeetingService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/meeting")
public class MeetingSummariesController {
    private final IMeetingSummariesRepo summaries;
    private final IMeetingRepo meetings;
    private final IUserRepo users;
    private final IParticipantsRepo participants;
    private final GeminiMeetingService ai;
    public MeetingSummariesController(IMeetingSummariesRepo summaries, IMeetingRepo meetings,
            IUserRepo users, IParticipantsRepo participants, GeminiMeetingService ai) {
        this.summaries = summaries;
        this.meetings = meetings;
        this.users = users;
        this.participants = participants;
        this.ai = ai;
    }
    public record SummaryView(Long id, String summary, String transcript, LocalDateTime createdAt) {}
    private SummaryView view(MeetingSummaries value) {
        return new SummaryView(value.getId(), value.getSummary(), value.getTranscript(), value.getCreateAt());
    }
    private Meeting authorize(Long meetingId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        Meeting meeting = meetings.findById(meetingId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuộc họp không tồn tại."));
        User user = users.findByUserName(auth.getName());
        if (user == null || (!(meeting.getUser() != null && Objects.equals(meeting.getUser().getId(), user.getId()))
                && participants.findByUserIdAndMeetingId(user.getId(), meetingId) == null))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập cuộc họp này.");
        return meeting;
    }
    @GetMapping("/{meetingId}/summary")
    public ResponseEntity<SummaryView> get(@PathVariable Long meetingId, Authentication auth) {
        authorize(meetingId, auth);
        return summaries.findFirstByMeetingIdOrderByCreateAtDescIdDesc(meetingId)
                .map(value -> ResponseEntity.ok(view(value))).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @PostMapping(value = "/upload-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SummaryView summarize(@RequestParam MultipartFile audio, @RequestParam Long meetingId,
                                 Authentication auth) throws IOException {
        Meeting meeting = authorize(meetingId, auth);
        if (audio.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hãy chọn bản ghi có âm thanh.");
        if (audio.getSize() > 14 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Bản ghi AI tối đa 14 MB. Hãy cắt ngắn hoặc nén bản ghi.");
        String name = Optional.ofNullable(audio.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String extension = name.substring(name.lastIndexOf('.') + 1);
        String mime = Map.of("mp3", "audio/mpeg", "wav", "audio/wav", "webm", "audio/webm",
                "ogg", "audio/ogg", "flac", "audio/flac", "m4a", "audio/mp4", "mp4", "video/mp4").get(extension);
        if (mime == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Hỗ trợ MP3, WAV, WEBM, OGG, FLAC, M4A và MP4.");
        GeminiMeetingService.Result result = ai.summarize(audio.getBytes(), mime);
        MeetingSummaries saved = new MeetingSummaries();
        saved.setMeeting(meeting);
        saved.setSummary(result.summary());
        saved.setTranscript(result.transcript());
        saved.setCreateAt(LocalDateTime.now());
        return view(summaries.save(saved));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", Objects.requireNonNullElse(e.getReason(), "Xử lý AI thất bại.")));
    }
}
