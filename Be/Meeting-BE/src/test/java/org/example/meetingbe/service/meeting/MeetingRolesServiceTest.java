package org.example.meetingbe.service.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetingRolesServiceTest {
    private IMeetingRepo meetings;
    private IUserRepo users;
    private IParticipantsRepo participants;
    private IMeetingRoleHistoryRepo history;
    private MeetingRoomSessions sessions;
    private MeetingRolesService service;
    private Meeting meeting;
    private final Map<Long, User> accounts = new HashMap<>();
    private final Map<Long, Participants> memberships = new HashMap<>();

    @BeforeEach
    void setup() {
        meetings = mock(IMeetingRepo.class);
        users = mock(IUserRepo.class);
        participants = mock(IParticipantsRepo.class);
        history = mock(IMeetingRoleHistoryRepo.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        sessions = new MeetingRoomSessions(new ObjectMapper());
        service = new MeetingRolesService(meetings, users, participants, history,
                new TransactionTemplate(manager), sessions);
        for (long id = 1; id <= 4; id++) {
            User user = new User();
            user.setId(id);
            user.setUserName("user" + id);
            accounts.put(id, user);
            Participants member = new Participants(LocalDateTime.now(), null, null, user);
            memberships.put(id, member);
        }
        meeting = new Meeting();
        meeting.setId(10L);
        meeting.setCode("room");
        meeting.setUser(accounts.get(1L));
        meeting.setCreator(accounts.get(1L));
        when(meetings.findForUpdate("room")).thenAnswer(call -> Optional.of(meeting));
        when(users.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(accounts.get(call.getArgument(0))));
        when(users.findByUserName(anyString())).thenAnswer(call -> accounts.values().stream()
                .filter(user -> user.getUserName().equals(call.getArgument(0))).findFirst().orElse(null));
        when(participants.findByUserIdAndMeetingId(anyLong(), eq(10L)))
                .thenAnswer(call -> memberships.get(call.getArgument(0)));
        connect(1L, "s1");
        connect(2L, "s2");
        connect(3L, "s3");
    }

    private WebSocketSession socket(String id) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        return socket;
    }

    private void connect(Long id, String sessionId) {
        sessions.add("room", id, accounts.get(id).getUserName(), socket(sessionId));
    }

    @Test
    void transferUpdatesHostClearsPromotedCoHostAndRecordsHistory() {
        meeting.setCoHost(accounts.get(2L));
        service.change("room", "user1", "TRANSFER_HOST", 2L, 0L);
        assertEquals(2L, meeting.getUser().getId());
        assertNull(meeting.getCoHost());
        assertEquals(1L, meeting.getCreator().getId());
        assertEquals("PARTICIPANT", service.state("room", "user1").participants().stream()
                .filter(p -> p.userId().equals(1L)).findFirst().orElseThrow().role());
        verify(history).save(argThat(event -> event.getAction().equals("TRANSFER_HOST")
                && event.getPreviousHostId().equals(1L) && event.getNewHostId().equals(2L)));
    }

    @Test
    void transferToOrdinaryMemberRetainsExistingCoHost() {
        meeting.setCoHost(accounts.get(2L));
        service.change("room", "user1", "TRANSFER_HOST", 3L, 0L);
        assertEquals(3L, meeting.getUser().getId());
        assertEquals(2L, meeting.getCoHost().getId());
    }

    @Test
    void coHostCannotTransferOrAppointCoHostOrEndMeeting() {
        meeting.setCoHost(accounts.get(2L));
        for (String action : List.of("TRANSFER_HOST", "SET_CO_HOST", "END", "REVOKE_CO_HOST"))
            assertThrows(ResponseStatusException.class,
                    () -> service.change("room", "user2", action, 3L, 0L));
        assertEquals(1L, meeting.getUser().getId());
        verifyNoInteractions(history);
    }

    @Test
    void ordinaryMemberCannotModerate() {
        assertThrows(ResponseStatusException.class,
                () -> service.change("room", "user3", "MUTE", 2L, 0L));
    }

    @Test
    void coHostCanRemoveOrdinaryMemberButCannotRemoveHost() {
        meeting.setCoHost(accounts.get(2L));
        assertThrows(ResponseStatusException.class,
                () -> service.change("room", "user2", "REMOVE", 1L, 0L));
        var state = service.change("room", "user2", "REMOVE", 3L, 0L);
        assertTrue(memberships.get(3L).isRemoved());
        assertFalse(sessions.online("room", 3L));
        assertFalse(state.participants().stream().anyMatch(p -> p.userId().equals(3L)));
    }

    @Test
    void removedMemberCannotRejoin() {
        service.change("room", "user1", "REMOVE", 3L, 0L);
        assertThrows(ResponseStatusException.class,
                () -> service.join("room", "user3", socket("rejoin")));
    }

    @Test
    void staleRequestAndOfflineTargetKeepCurrentHost() {
        assertThrows(ResponseStatusException.class,
                () -> service.change("room", "user1", "TRANSFER_HOST", 2L, 99L));
        sessions.remove("s2");
        assertThrows(ResponseStatusException.class,
                () -> service.change("room", "user1", "TRANSFER_HOST", 2L, 0L));
        assertEquals(1L, meeting.getUser().getId());
        verifyNoInteractions(history);
    }

    @Test
    void hostLeavingImmediatelyPromotesOnlineCoHost() {
        meeting.setCoHost(accounts.get(2L));
        service.leave("room", "user1", true);
        assertEquals(2L, meeting.getUser().getId());
        assertNull(meeting.getCoHost());
        assertFalse(sessions.online("room", 1L));
        assertNotNull(memberships.get(1L).getLeftAt());
    }

    @Test
    void hostMustChooseSuccessorBeforeLeavingOtherMembers() {
        assertThrows(ResponseStatusException.class, () -> service.leave("room", "user1", true));
        assertTrue(sessions.online("room", 1L));
        assertNull(memberships.get(1L).getLeftAt());
    }

    @Test
    void coHostLeavingClearsOnlyCoHost() {
        meeting.setCoHost(accounts.get(2L));
        service.leave("room", "user2", true);
        assertNull(meeting.getCoHost());
        assertEquals(1L, meeting.getUser().getId());
    }

    @Test
    void creatorReturningRestoresHostButOldTransferredHostDoesNot() {
        meeting.setUser(accounts.get(2L));
        sessions.remove("s1");
        service.join("room", "user1", socket("creator-return"));
        assertEquals(1L, meeting.getUser().getId());
        meeting.setUser(accounts.get(3L));
        sessions.remove("s2");
        service.join("room", "user2", socket("old-host-return"));
        assertEquals(3L, meeting.getUser().getId());
    }

    @Test
    void refreshOfAnotherTabDoesNotReclaimHostWhileCreatorIsStillPresent() {
        meeting.setUser(accounts.get(2L));
        service.join("room", "user1", socket("creator-second-tab"));
        assertEquals(2L, meeting.getUser().getId());
    }

    @Test
    void aClosedTabDoesNotRemoveAnotherConnectionOfSameUser() {
        connect(1L, "host-second-tab");
        service.disconnected("s1");
        assertTrue(sessions.online("room", 1L));
        assertEquals(1L, meeting.getUser().getId());
    }

    @Test
    void joiningAndLeavingAdvanceSnapshotVersionEvenWhenRolesDoNotChange() {
        service.join("room", "user4", socket("s4"));
        long joinedVersion = service.state("room", "user1").version();
        assertTrue(joinedVersion > 0);
        service.leave("room", "user4", true);
        assertTrue(service.state("room", "user1").version() > joinedVersion);
        service.disconnected("s3");
        assertTrue(service.state("room", "user1").version() > joinedVersion + 1);
    }

    @Test
    void hostDisconnectWaitsForGraceAndReconnectCancelsSuccession() {
        meeting.setCoHost(accounts.get(2L));
        service.disconnected("s1");
        service.expireDisconnected();
        assertEquals(1L, meeting.getUser().getId());
        service.join("room", "user1", socket("reconnected"));
        service.expireDisconnected();
        assertEquals(1L, meeting.getUser().getId());
    }

    @Test
    void coHostSucceedsAfterDisconnectedHostGraceExpires() {
        meeting.setCoHost(accounts.get(2L));
        service.disconnected("s1");
        Instant later = Instant.now().plusSeconds(31);
        sessions.all().forEach(c -> c.socket().getAttributes().put("meetingLastSeen", later));
        service.expireDisconnected(later);
        assertEquals(2L, meeting.getUser().getId());
        assertNull(meeting.getCoHost());
        verify(history).save(argThat(event -> event.getAction().equals("HOST_SUCCEEDED")));
    }

    @Test
    void coHostWhoAlsoDisconnectedCannotInheritHost() {
        meeting.setCoHost(accounts.get(2L));
        service.disconnected("s1");
        service.disconnected("s2");
        Instant later = Instant.now().plusSeconds(31);
        sessions.all().forEach(c -> c.socket().getAttributes().put("meetingLastSeen", later));
        service.expireDisconnected(later);
        assertEquals(1L, meeting.getUser().getId());
        assertNull(meeting.getCoHost());
    }

    @Test
    void coHostRejoiningAfterServerRestartSucceedsWhenHostDoesNotReturn() {
        meeting.setCoHost(accounts.get(2L));
        sessions.remove("s1");
        sessions.remove("s2");
        service.join("room", "user2", socket("cohost-restart"));
        Instant later = Instant.now().plusSeconds(31);
        sessions.all().forEach(c -> c.socket().getAttributes().put("meetingLastSeen", later));
        service.expireDisconnected(later);
        assertEquals(2L, meeting.getUser().getId());
        assertNull(meeting.getCoHost());
    }

    @Test
    void lastHostLeavingClosesRoomAndEndedRoomRejectsJoin() {
        sessions.remove("s2");
        sessions.remove("s3");
        service.leave("room", "user1", true);
        assertNotNull(meeting.getEndTime());
        assertThrows(ResponseStatusException.class,
                () -> service.join("room", "user2", socket("new")));
    }

    @Test
    void endingMeetingMarksParticipantsLeftAndClosesConnections() {
        var result = service.change("room", "user1", "END", null, 0L);
        assertTrue(result.ended());
        assertTrue(sessions.inRoom("room").isEmpty());
        assertNotNull(memberships.get(2L).getLeftAt());
    }

    @Test
    void simultaneousTransfersAcceptOnlyOneRequest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> transfer(2L);
            Callable<Boolean> second = () -> transfer(3L);
            List<Future<Boolean>> results = executor.invokeAll(List.of(first, second));
            assertEquals(1, results.stream().filter(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).count());
            verify(history, times(1)).save(any());
        } finally { executor.shutdownNow(); }
    }

    private boolean transfer(Long target) {
        try { service.change("room", "user1", "TRANSFER_HOST", target, 0L); return true; }
        catch (ResponseStatusException e) { return false; }
    }
}
