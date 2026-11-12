package org.example.meetingbe.controller;

import org.example.meetingbe.dto.MeetingDto;
import org.example.meetingbe.model.Participants;
import org.example.meetingbe.repository.IParticipantsRepo;
import org.example.meetingbe.service.meeting.MeetingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.example.meetingbe.repository.IUserRepo;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200/", allowedHeaders = "*")
public class MeetingController {

    @Autowired
    private MeetingService meetingService;
    @Autowired
    private IUserRepo users;

    @PostMapping("/meetings")
    public MeetingDto createRoom(@RequestBody Map<String, Object> request, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        Long hostId = users.findByUserName(auth.getName()).getId();
        String title = request.get("title").toString();
        return meetingService.createRoom(hostId, title);
    }

    @GetMapping("/{roomId}")
    public MeetingDto getRoom(@PathVariable String roomId) {
        return meetingService.getRoom(roomId);
    }

    @PostMapping("/{roomId}/join")
    public void joinRoom(@PathVariable String roomId, @RequestBody Long userId) {
        meetingService.addParticipant(roomId, userId);
    }
}
