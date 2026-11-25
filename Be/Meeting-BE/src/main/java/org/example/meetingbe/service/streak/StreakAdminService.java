package org.example.meetingbe.service.streak;

import org.example.meetingbe.model.StreakReward;
import org.example.meetingbe.model.UserStreak;
import org.example.meetingbe.repository.StreakRewardRepository;
import org.example.meetingbe.repository.UserStreakRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StreakAdminService {

    @Autowired
    private StreakRewardRepository streakRewardRepository;

    @Autowired
    private UserStreakRepository userStreakRepository;

    // Lấy danh sách mốc thưởng sắp xếp theo số ngày tăng dần
    public List<StreakReward> getAllRewards() {
        return streakRewardRepository.findAllByOrderByStreakDaysAsc();
    }

    // Thêm mốc thưởng mới
    public StreakReward createReward(StreakReward reward) {
        if (streakRewardRepository.existsByStreakDays(reward.getStreakDays())) {
            throw new RuntimeException("Mốc " + reward.getStreakDays() + " ngày đã tồn tại!");
        }
        if (reward.getIsActive() == null) {
            reward.setIsActive(true);
        }
        return streakRewardRepository.save(reward);
    }

    // Cập nhật mốc thưởng
    public StreakReward updateReward(Long id, StreakReward updated) {
        StreakReward existing = streakRewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mốc thưởng ID: " + id));

        if (!existing.getStreakDays().equals(updated.getStreakDays())
                && streakRewardRepository.existsByStreakDays(updated.getStreakDays())) {
            throw new RuntimeException("Mốc " + updated.getStreakDays() + " ngày đã tồn tại!");
        }

        existing.setStreakDays(updated.getStreakDays());
        existing.setRewardName(updated.getRewardName());
        existing.setRewardType(updated.getRewardType());
        existing.setRewardValue(updated.getRewardValue());
        existing.setDescription(updated.getDescription());
        if (updated.getIsActive() != null) {
            existing.setIsActive(updated.getIsActive());
        }
        return streakRewardRepository.save(existing);
    }

    // Bật/Tắt nhanh trạng thái mốc thưởng
    public StreakReward toggleStatus(Long id) {
        StreakReward existing = streakRewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mốc thưởng ID: " + id));
        existing.setIsActive(!Boolean.TRUE.equals(existing.getIsActive()));
        return streakRewardRepository.save(existing);
    }

    // Xóa mốc thưởng
    public void deleteReward(Long id) {
        if (!streakRewardRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy mốc thưởng ID: " + id);
        }
        streakRewardRepository.deleteById(id);
    }

    // Lấy danh sách chuỗi học tập của học viên (Client), loại trừ tài khoản ADMIN
    public List<UserStreak> getAllUserStreaks() {
        return userStreakRepository.findAllByOrderByCurrentStreakDesc()
                .stream()
                .filter(us -> us.getUser().getRoles().stream()
                        .noneMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleName())))
                .toList();
    }
}