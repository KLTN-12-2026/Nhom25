package org.example.meetingbe.controller;

import org.example.meetingbe.service.streak.StreakClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/client/streak")
@CrossOrigin("*")
public class StreakClientController {

    @Autowired
    private StreakClientService streakClientService;

    @PostMapping("/checkin/{userName}")
    public ResponseEntity<?> dailyCheckin(@PathVariable String userName) {
        try {
            return ResponseEntity.ok(streakClientService.dailyCheckin(userName));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}