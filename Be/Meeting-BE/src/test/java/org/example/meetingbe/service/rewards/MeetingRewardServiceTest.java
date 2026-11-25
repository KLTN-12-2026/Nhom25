package org.example.meetingbe.service.rewards;

import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MeetingRewardServiceTest {
    RewardAccountRepository accounts=mock(RewardAccountRepository.class);
    RewardAttendanceRepository attendance=mock(RewardAttendanceRepository.class);
    RewardEntryRepository entries=mock(RewardEntryRepository.class);
    RewardGiftRepository gifts=mock(RewardGiftRepository.class);
    RewardRulesRepository rules=mock(RewardRulesRepository.class);
    RewardClaimRepository claims=mock(RewardClaimRepository.class);
    IUserRepo users=mock(IUserRepo.class);
    UserStreakRepository legacy=mock(UserStreakRepository.class);
    MeetingRewardService service;
    RewardAccount account;
    RewardAttendance visit;
    RewardRules config;
    User user;
    Meeting meeting;

    @BeforeEach void setup() {
        service=new MeetingRewardService(accounts,attendance,entries,gifts,rules,claims,users,legacy);
        user=new User();user.setId(1L);user.setUserName("learner");user.setVip(false);
        meeting=new Meeting();meeting.setId(5L);meeting.setCode("room");
        account=new RewardAccount();account.userId=1L;
        visit=new RewardAttendance();visit.userId=1L;visit.meetingId=5L;visit.meetingCode="room";
        visit.connectedAt=Instant.now().minusSeconds(901);
        config=new RewardRules();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.findByUserName("learner")).thenReturn(user);
        when(users.lockRewardUserByName("learner")).thenReturn(Optional.of(user));
        when(users.lockRewardUser(1L)).thenReturn(Optional.of(user));
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenAnswer(c -> c.getArgument(0));
        when(attendance.findByUserIdAndMeetingId(1L,5L)).thenReturn(Optional.of(visit));
        when(attendance.countByMeetingId(5L)).thenReturn(2L);
        when(rules.findById(1L)).thenReturn(Optional.of(config));
    }
    @Test void firstMeetingAwardsBaseAndDailyBonusExactlyOnce() {
        service.left(meeting,1L);service.left(meeting,1L);
        assertEquals(15,account.balance);assertEquals(15,account.lifetimePoints);
        assertEquals(1,account.meetingCount);assertTrue(visit.credited);
        verify(entries,times(1)).save(any());
    }
    @Test void shortOrSoloMeetingDoesNotEarnPoints() {
        visit.connectedAt=Instant.now().minusSeconds(100);
        service.left(meeting,1L);assertEquals(0,account.balance);
        visit.connectedAt=Instant.now().minusSeconds(1000);
        when(attendance.countByMeetingId(5L)).thenReturn(1L);
        service.left(meeting,1L);assertEquals(0,account.balance);
    }
    @Test void reconnectAccumulatesTimeWithoutDoubleCountingTabs() {
        visit.seconds=500;visit.connectedAt=Instant.now().minusSeconds(450);
        Instant original=visit.connectedAt;
        service.joined(meeting,user);assertEquals(original,visit.connectedAt);
        service.left(meeting,1L);assertTrue(visit.credited);assertTrue(visit.seconds>=950);
        service.joined(meeting,user);service.left(meeting,1L);
        assertEquals(15,account.balance);
    }
    @Test void dailyLimitPreventsPointsAndMilestoneProgress() {
        when(attendance.countByUserIdAndCreditedDate(eq(1L),any())).thenReturn(3L);
        service.left(meeting,1L);
        assertFalse(visit.credited);assertEquals(0,account.meetingCount);assertEquals(0,account.balance);
    }
    @Test void tenthMeetingAwardsMilestoneOnce() {
        account.meetingCount=9;
        when(attendance.countByUserIdAndCreditedDate(eq(1L),any())).thenReturn(1L);
        service.left(meeting,1L);
        assertEquals(40,account.balance);assertTrue(account.firstMilestoneClaimed);
        // Changing a rule cannot reissue the same milestone reward.
        config.firstMilestone=11;visit.credited=false;visit.connectedAt=Instant.now().minusSeconds(901);
        service.left(meeting,1L);assertEquals(50,account.balance);
    }
    @Test void redeemSubtractsBalanceWithoutReducingLifetimeAndExtendsVip() {
        account.balance=600;account.lifetimePoints=600;
        Instant expiration=Instant.now().plus(Duration.ofDays(3));user.setRewardVipExpiresAt(expiration);
        RewardGift gift=new RewardGift();gift.id=2L;gift.type="VIP";gift.name="VIP 7 ngày";gift.cost=300;gift.vipDays=7;
        when(gifts.findById(2L)).thenReturn(Optional.of(gift));
        service.redeem("learner",2L);
        assertEquals(300,account.balance);assertEquals(600,account.lifetimePoints);
        assertEquals(expiration.plus(Duration.ofDays(7)),user.getRewardVipExpiresAt());
        assertTrue(user.getVip());verify(claims).save(any());
    }
    @Test void insufficientBalanceAndDisabledGiftAreRejectedWithoutDebit() {
        RewardGift g=new RewardGift();g.id=2L;g.name="VIP";g.cost=300;g.vipDays=7;
        when(gifts.findById(2L)).thenReturn(Optional.of(g));
        assertThrows(RuntimeException.class,()->service.redeem("learner",2L));
        account.balance=500;g.active=false;
        assertThrows(RuntimeException.class,()->service.redeem("learner",2L));
        assertEquals(500,account.balance);verify(entries,never()).save(any());
    }
    @Test void badgeCannotBeClaimedTwice() {
        account.balance=500;
        RewardGift g=new RewardGift();g.id=2L;g.name="Huy hiệu";g.type="BADGE";g.cost=100;
        when(gifts.findById(2L)).thenReturn(Optional.of(g));
        when(claims.existsByUserIdAndGiftId(1L,2L)).thenReturn(true);
        assertThrows(RuntimeException.class,()->service.redeem("learner",2L));
        assertEquals(500,account.balance);
    }
    @Test void oldPointsAreTransferredOnFirstAccountCreation() {
        when(accounts.lockById(1L)).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any())).thenAnswer(c->c.getArgument(0));
        UserStreak previous=new UserStreak();previous.setTotalPoints(70);
        when(legacy.findByUserId(1L)).thenReturn(Optional.of(previous));
        assertEquals(70,service.summary("learner").get("balance"));
        verify(entries).save(argThat(e->e.points==70 && e.meetingCode==null));
    }
    @Test void expiredRewardVipDoesNotRemoveExistingPaidVip() {
        user.setRewardVipExpiresAt(Instant.now().minusSeconds(1));assertFalse(user.getVip());
        user.setVip(true);assertTrue(user.getVip());
    }
    @Test void negativeAdjustmentCannotOverdrawBalance() {
        account.balance=10;
        assertThrows(RuntimeException.class,()->service.adjust(1L,-11,"Điều chỉnh"));
        assertEquals(10,account.balance);verify(entries,never()).save(any());
    }
    @Test void restartNeverCountsDowntimeAsMeetingTime() {
        when(attendance.findByConnectedAtIsNotNull()).thenReturn(List.of(visit));
        when(gifts.count()).thenReturn(1L);
        service.clearStaleConnections();
        assertNull(visit.connectedAt);assertEquals(0,visit.seconds);
    }
}
