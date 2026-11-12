package org.example.meetingbe.controller;

import jakarta.validation.Valid;
import org.example.meetingbe.dto.*;
import org.example.meetingbe.service.meeting.MeetingRolesService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/meetings/{roomCode}")
public class MeetingRolesController {
    private final MeetingRolesService roles;
    public MeetingRolesController(MeetingRolesService roles) { this.roles = roles; }

    private String actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        return authentication.getName();
    }

    @GetMapping("/roles")
    public MeetingRolesDto state(@PathVariable String roomCode, Authentication auth) {
        return roles.state(roomCode, actor(auth));
    }

    @PostMapping("/actions/{action}")
    public MeetingRolesDto change(@PathVariable String roomCode, @PathVariable String action,
                                 @Valid @RequestBody MeetingRoleRequest request, Authentication auth) {
        return roles.change(roomCode, actor(auth), action, request.targetUserId(), request.version());
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@PathVariable String roomCode, Authentication auth) {
        roles.leave(roomCode, actor(auth), true);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                e.getReason() == null ? "Không thực hiện được thao tác." : e.getReason()));
    }
}
