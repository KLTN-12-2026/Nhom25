package org.example.meetingbe.controller;
import org.example.meetingbe.model.*;
import org.example.meetingbe.service.rewards.MeetingRewardService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/rewards")
@CrossOrigin(origins="http://localhost:4200")
public class MeetingRewardController {
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<?> error(ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.toString(e.getReason(),"Không thực hiện được yêu cầu.")));
    }
    private final MeetingRewardService service;
    public MeetingRewardController(MeetingRewardService service) { this.service=service; }
    private String name(Authentication a) {
        if(a==null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập.");
        return a.getName();
    }
    private void admin(Authentication a) {
        name(a);
        if(a.getAuthorities().stream().noneMatch(r -> "ADMIN".equals(r.getAuthority()) || "ROLE_ADMIN".equals(r.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ admin được quản lý điểm thưởng.");
    }
    @GetMapping("/me") public Object me(Authentication a) { return service.summary(name(a)); }
    @PostMapping("/redeem/{id}") public Object redeem(Authentication a,@PathVariable Long id) { return service.redeem(name(a),id); }
    @GetMapping("/admin/rules") public Object rules(Authentication a) { admin(a); return service.rules(); }
    @PutMapping("/admin/rules") public Object rules(Authentication a,@RequestBody RewardRules r) { admin(a); return service.updateRules(r); }
    @GetMapping("/admin/gifts") public Object gifts(Authentication a) { admin(a); return service.gifts(); }
    @PostMapping("/admin/gifts") public Object create(Authentication a,@RequestBody RewardGift g) { admin(a); return service.saveGift(null,g); }
    @PutMapping("/admin/gifts/{id}") public Object update(Authentication a,@PathVariable Long id,@RequestBody RewardGift g) { admin(a); return service.saveGift(id,g); }
    @GetMapping("/admin/users") public Object users(Authentication a) { admin(a); return service.users(); }
    @GetMapping("/admin/users/{id}/history") public Object history(Authentication a,@PathVariable Long id) { admin(a); return service.history(id); }
    public record Adjustment(int points,String reason) {}
    @PostMapping("/admin/users/{id}/adjust") public void adjust(Authentication a,@PathVariable Long id,@RequestBody Adjustment v) { admin(a); service.adjust(id,v.points(),v.reason()); }
}
