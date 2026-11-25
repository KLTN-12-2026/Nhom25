package org.example.meetingbe.service.rewards;

import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class MeetingRewardService {
    private final RewardAccountRepository accounts;
    private final RewardAttendanceRepository attendance;
    private final RewardEntryRepository entries;
    private final RewardGiftRepository gifts;
    private final RewardRulesRepository rules;
    private final RewardClaimRepository claims;
    private final IUserRepo users;
    private final UserStreakRepository legacy;
    public MeetingRewardService(RewardAccountRepository accounts, RewardAttendanceRepository attendance,
            RewardEntryRepository entries, RewardGiftRepository gifts, RewardRulesRepository rules,
            RewardClaimRepository claims, IUserRepo users, UserStreakRepository legacy) {
        this.accounts=accounts; this.attendance=attendance; this.entries=entries;
        this.gifts=gifts; this.rules=rules; this.claims=claims; this.users=users; this.legacy=legacy;
    }
    private boolean admin(User user) {
        return user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getRoleName()));
    }
    private RewardAccount account(Long userId) {
        // Lock the existing user first: also serializes creation of a new reward account.
        users.lockRewardUser(userId).orElseThrow();
        return accounts.lockById(userId).orElseGet(() -> {
            RewardAccount a = new RewardAccount(); a.userId=userId;
            int previous=legacy.findByUserId(userId).map(s -> s.getTotalPoints()==null ? 0 : s.getTotalPoints()).orElse(0);
            a=accounts.saveAndFlush(a);
            if(previous>0) add(a,previous,"Chuyển điểm tích lũy từ hệ thống cũ",null);
            return a;
        });
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void clearStaleConnections() {
        // Never count server downtime as time spent in a meeting.
        attendance.findByConnectedAtIsNotNull().forEach(a -> { a.connectedAt=null; attendance.save(a); });
        if(gifts.count()==0) {
            seedGift("Thành viên tích cực","Huy hiệu ghi nhận hoạt động họp của bạn.","BADGE",100,0);
            seedGift("VIP 7 ngày","Trải nghiệm quyền lợi VIP trong 7 ngày.","VIP",300,7);
            seedGift("VIP 30 ngày","Nâng cấp trải nghiệm họp trong 30 ngày.","VIP",1000,30);
        }
    }
    private void seedGift(String name,String description,String type,int cost,int days) {
        RewardGift g=new RewardGift();g.name=name;g.description=description;g.type=type;g.cost=cost;g.vipDays=days;gifts.save(g);
    }
    public RewardRules rules() { return rules.findById(1L).orElseGet(RewardRules::new); }
    public RewardRules updateRules(RewardRules r) {
        if (r.minimumMinutes<1 || r.minimumMinutes>1440 || r.meetingPoints<1 || r.meetingPoints>10000
                || r.firstDailyBonus<0 || r.firstDailyBonus>10000 || r.dailyLimit<1 || r.dailyLimit>100
                || r.firstMilestone<1 || r.secondMilestone<=r.firstMilestone
                || r.firstMilestonePoints<0 || r.firstMilestonePoints>100000
                || r.secondMilestonePoints<0 || r.secondMilestonePoints>100000)
            throw bad("Quy tắc không hợp lệ. Mốc thứ hai phải lớn hơn mốc thứ nhất.");
        r.id=1L; return rules.save(r);
    }
    public void joined(Meeting meeting, User user) {
        account(user.getId());
        RewardAttendance a=attendance.findByUserIdAndMeetingId(user.getId(),meeting.getId()).orElseGet(() -> {
            RewardAttendance value=new RewardAttendance(); value.userId=user.getId();
            value.meetingId=meeting.getId(); value.meetingCode=meeting.getCode(); return value;
        });
        if (a.connectedAt==null) a.connectedAt=Instant.now();
        attendance.save(a);
    }
    public void left(Meeting meeting, Long userId) {
        RewardAttendance a=attendance.findByUserIdAndMeetingId(userId,meeting.getId()).orElse(null);
        if (a==null || a.connectedAt==null) return;
        RewardAccount account=account(userId);
        a.seconds+=Math.max(0,Duration.between(a.connectedAt,Instant.now()).getSeconds());
        a.connectedAt=null;
        RewardRules r=rules();
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        long daily=attendance.countByUserIdAndCreditedDate(userId,today);
        if (!admin(users.findById(userId).orElseThrow()) && !a.credited && a.seconds>=r.minimumMinutes*60L && attendance.countByMeetingId(meeting.getId())>=2
                && daily<r.dailyLimit) {
            a.credited=true; a.creditedDate=today; account.meetingCount++;
            int points=r.meetingPoints+(daily==0?r.firstDailyBonus:0);
            add(account,points,"Hoàn thành cuộc họp"+(daily==0?" + thưởng cuộc họp đầu ngày":""),meeting.getCode());
            if (!account.firstMilestoneClaimed && account.meetingCount==r.firstMilestone) {
                account.firstMilestoneClaimed=true;
                add(account,r.firstMilestonePoints,"Đạt mốc "+r.firstMilestone+" cuộc họp",meeting.getCode());
            }
            if (!account.secondMilestoneClaimed && account.meetingCount==r.secondMilestone) {
                account.secondMilestoneClaimed=true;
                add(account,r.secondMilestonePoints,"Đạt mốc "+r.secondMilestone+" cuộc họp",meeting.getCode());
            }
        }
        attendance.save(a);
    }
    private void add(RewardAccount a,int points,String description,String meetingCode) {
        a.balance+=points; if(points>0) a.lifetimePoints+=points; accounts.save(a);
        RewardEntry e=new RewardEntry(); e.userId=a.userId; e.points=points;
        e.description=description; e.meetingCode=meetingCode; entries.save(e);
    }
    public Map<String,Object> summary(String name) {
        User user=users.lockRewardUserByName(name).orElseThrow(() -> bad("Không tìm thấy tài khoản."));
        RewardAccount a=account(user.getId());
        return Map.of("balance",a.balance,"lifetimePoints",a.lifetimePoints,"meetingCount",a.meetingCount,
                "isAdmin",admin(user),"rules",rules(),"gifts",gifts.findAll().stream().filter(g -> g.active).toList(),
                "history",entries.findTop100ByUserIdOrderByIdDesc(user.getId()),
                "claims",claims.findTop100ByUserIdOrderByIdDesc(user.getId()),
                "vipExpiresAt",Objects.toString(user.getRewardVipExpiresAt(),""),
                "attendance",attendance.findTop30ByUserIdOrderByIdDesc(user.getId()).stream().map(visit -> {
                    String status=visit.credited ? "Đã cộng điểm" : visit.connectedAt!=null ? "Đang tham gia"
                            : visit.seconds<rules().minimumMinutes*60L ? "Chưa đủ thời gian"
                            : attendance.countByMeetingId(visit.meetingId)<2 ? "Phòng chưa đủ 2 tài khoản"
                            : "Chưa được tính điểm (giới hạn ngày hoặc tài khoản không áp dụng)";
                    return Map.<String,Object>of("meetingCode",visit.meetingCode,"minutes",visit.seconds/60,"status",status);
                }).toList());
    }
    public Map<String,Object> redeem(String name,Long giftId) {
        User user=users.lockRewardUserByName(name).orElseThrow(() -> bad("Không tìm thấy tài khoản."));
        if(admin(user)) throw bad("Chỉ tài khoản người dùng được đổi thưởng.");
        RewardAccount a=account(user.getId());
        RewardGift gift=gifts.findById(giftId).orElseThrow(() -> bad("Không tìm thấy quà."));
        if(!gift.active || gift.cost<1) throw bad("Quà hiện không thể đổi.");
        if(a.balance<gift.cost) throw bad("Bạn chưa đủ điểm để đổi quà này.");
        if("VIP".equals(gift.type)) {
            if(Boolean.TRUE.equals(user.getVip()) && user.getRewardVipExpiresAt()==null)
                throw bad("Tài khoản đã có VIP không thời hạn.");
            Instant start=user.getRewardVipExpiresAt()!=null && user.getRewardVipExpiresAt().isAfter(Instant.now())
                    ? user.getRewardVipExpiresAt():Instant.now();
            user.setRewardVipExpiresAt(start.plus(Duration.ofDays(gift.vipDays)));
            users.save(user);
        } else if(claims.existsByUserIdAndGiftId(user.getId(),giftId)) {
            throw bad("Bạn đã sở hữu huy hiệu này.");
        }
        add(a,-gift.cost,"Đổi quà: "+gift.name,null);
        RewardClaim claim=new RewardClaim(); claim.userId=user.getId(); claim.giftId=gift.id;
        claim.giftName=gift.name; claim.pointsSpent=gift.cost; claims.save(claim);
        return summary(name);
    }
    public List<RewardGift> gifts() { return gifts.findAll(); }
    public RewardGift saveGift(Long id,RewardGift g) {
        if(g.name==null || g.name.isBlank() || g.name.length()>150 || g.cost<1 || g.cost>1000000
                || !("VIP".equals(g.type)||"BADGE".equals(g.type))
                || ("VIP".equals(g.type)&&(g.vipDays<1||g.vipDays>365))) throw bad("Thông tin quà không hợp lệ.");
        if(id!=null && !gifts.existsById(id)) throw bad("Không tìm thấy quà.");
        g.id=id; return gifts.save(g);
    }
    public List<Map<String,Object>> users() {
        return accounts.findAll().stream().map(a -> {
            User u=users.findById(a.userId).orElseThrow();
            return Map.<String,Object>of("userId",a.userId,"userName",u.getUserName(),"balance",a.balance,
                    "lifetimePoints",a.lifetimePoints,"meetingCount",a.meetingCount);
        }).toList();
    }
    public void adjust(Long userId,int delta,String reason) {
        if(delta==0 || Math.abs((long)delta)>100000 || reason==null || reason.isBlank() || reason.length()>200)
            throw bad("Nhập điểm điều chỉnh và lý do (tối đa 200 ký tự).");
        RewardAccount a=account(userId);
        if((long)a.balance+delta<0) throw bad("Điều chỉnh làm số dư âm.");
        add(a,delta,"Admin điều chỉnh: "+reason,null);
    }
    public List<RewardEntry> history(Long userId) { return entries.findTop100ByUserIdOrderByIdDesc(userId); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
}
