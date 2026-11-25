package org.example.meetingbe.controller;

import org.example.meetingbe.model.StreakReward;
import org.example.meetingbe.model.UserStreak;
import org.example.meetingbe.service.streak.StreakAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/streak")
@CrossOrigin("*")
public class StreakAdminController {

    @Autowired
    private StreakAdminService streakAdminService;

    // 1. Lấy danh sách mốc thưởng
    @GetMapping("/rewards")
    public ResponseEntity<List<StreakReward>> getAllRewards() {
        return ResponseEntity.ok(streakAdminService.getAllRewards());
    }

    // 2. Tạo mốc thưởng mới
    @PostMapping("/rewards")
    public ResponseEntity<?> createReward(@RequestBody StreakReward reward) {
        try {
            return ResponseEntity.ok(streakAdminService.createReward(reward));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 3. Cập nhật mốc thưởng
    @PutMapping("/rewards/{id}")
    public ResponseEntity<?> updateReward(@PathVariable Long id, @RequestBody StreakReward reward) {
        try {
            return ResponseEntity.ok(streakAdminService.updateReward(id, reward));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 4. Bật/Tắt trạng thái mốc thưởng
    @PatchMapping("/rewards/{id}/toggle")
    public ResponseEntity<?> toggleStatus(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(streakAdminService.toggleStatus(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 5. Xóa mốc thưởng
    @DeleteMapping("/rewards/{id}")
    public ResponseEntity<?> deleteReward(@PathVariable Long id) {
        try {
            streakAdminService.deleteReward(id);
            return ResponseEntity.ok(Map.of("message", "Xóa mốc thưởng thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 6. Thống kê chuỗi của học viên (Client)
    @GetMapping("/users")
    public ResponseEntity<List<UserStreak>> getAllUserStreaks() {
        return ResponseEntity.ok(streakAdminService.getAllUserStreaks());
    }
}