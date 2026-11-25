package org.example.meetingbe.service.streak;

import org.example.meetingbe.model.StreakLog;
import org.example.meetingbe.model.StreakReward;
import org.example.meetingbe.model.User;
import org.example.meetingbe.model.UserStreak;
import org.example.meetingbe.repository.IUserRepo;
import org.example.meetingbe.repository.StreakLogRepository;
import org.example.meetingbe.repository.StreakRewardRepository;
import org.example.meetingbe.repository.UserStreakRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StreakClientService {

    @Autowired
    private UserStreakRepository userStreakRepository;

    @Autowired
    private StreakRewardRepository streakRewardRepository;

    @Autowired
    private StreakLogRepository streakLogRepository;

    @Autowired
    private IUserRepo userRepo;

    @Transactional
    public Map<String, Object> dailyCheckin(String userName) {
        User user = userRepo.findByUserName(userName);
        if (user == null) {
            throw new RuntimeException("Không tìm thấy tài khoản: " + userName);
        }

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getRoleName()));
        if (isAdmin) {
            return Map.of("isAdmin", true);
        }

        LocalDate today = LocalDate.now();
        UserStreak userStreak = userStreakRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserStreak ns = new UserStreak();
                    ns.setUser(user);
                    ns.setCurrentStreak(0);
                    ns.setLongestStreak(0);
                    ns.setTotalPoints(0);
                    return ns;
                });

        boolean incrementedToday = false;
        StreakReward unlockedReward = null;

        List<StreakReward> activeRewards = streakRewardRepository.findAllByOrderByStreakDaysAsc()
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .toList();

        List<String> checkinDates = streakLogRepository.findAllByUserIdOrderByCheckinDateAsc(user.getId())
                .stream()
                .map(log -> log.getCheckinDate().toString())
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("isAdmin", false);
        result.put("currentStreak", userStreak.getCurrentStreak());
        result.put("longestStreak", userStreak.getLongestStreak());
        result.put("totalPoints", userStreak.getTotalPoints());
        result.put("lastCheckinDate", userStreak.getLastCheckinDate());
        result.put("incrementedToday", incrementedToday);
        result.put("unlockedReward", unlockedReward);
        result.put("rewards", activeRewards);
        result.put("checkinDates", checkinDates);
        result.put("accountCreatedDate", user.getCreateAt() != null ? user.getCreateAt().toLocalDate().toString() : today.toString());
        return result;
    }
}
