package org.example.meetingbe.service.meeting;

import org.example.meetingbe.dto.MeetingRolesDto;
import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MeetingRolesService {
    private static final Logger log = LoggerFactory.getLogger(MeetingRolesService.class);
    private record Absence(String room, Long userId, Instant deadline) {}
    private final Map<String, Absence> absences = new ConcurrentHashMap<>();
    private final IMeetingRepo meetings;
    private final IUserRepo users;
    private final IParticipantsRepo participants;
    private final IMeetingRoleHistoryRepo history;
    private final TransactionTemplate transactions;
    private final MeetingRoomSessions sessions;
    @org.springframework.beans.factory.annotation.Autowired
    private org.example.meetingbe.service.rewards.MeetingRewardService rewards;

    public MeetingRolesService(IMeetingRepo meetings, IUserRepo users, IParticipantsRepo participants,
            IMeetingRoleHistoryRepo history, TransactionTemplate transactions, MeetingRoomSessions sessions) {
        this.meetings = meetings;
        this.users = users;
        this.participants = participants;
        this.history = history;
        this.transactions = transactions;
        this.sessions = sessions;
    }

    private Meeting locked(String room) {
        Meeting meeting = meetings.findForUpdate(room).orElseThrow(() ->
                error(HttpStatus.NOT_FOUND, "Cuộc họp không tồn tại."));
        // Existing rooms predate creator_id. Freeze the original host before changing roles.
        if (meeting.getCreator() == null) meeting.setCreator(meeting.getUser());
        return meeting;
    }

    private User user(String name) {
        User value = users.findByUserName(name);
        if (value == null) throw error(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại.");
        return value;
    }

    private void open(Meeting meeting) {
        if (meeting.getEndTime() != null)
            throw error(HttpStatus.CONFLICT, "Cuộc họp đã kết thúc.");
    }

    private Participants membership(Meeting meeting, Long userId) {
        return participants.findByUserIdAndMeetingId(userId, meeting.getId());
    }

    private void joined(Meeting meeting, User user) {
        Participants member = membership(meeting, user.getId());
        if (member != null && member.isRemoved())
            throw error(HttpStatus.FORBIDDEN, "Bạn đã bị xóa khỏi cuộc họp này.");
        if (member == null) member = new Participants(LocalDateTime.now(), null, meeting, user);
        if (member.getLeftAt() != null) member.setJoinAt(LocalDateTime.now());
        member.setLeftAt(null);
        participants.save(member);
        if (rewards != null) rewards.joined(meeting, user);
    }

    private void active(Meeting meeting, User user) {
        open(meeting);
        if (!sessions.online(meeting.getCode(), user.getId()))
            throw error(HttpStatus.CONFLICT, "Người tham gia đã rời phòng hoặc mất kết nối.");
        Participants member = membership(meeting, user.getId());
        if (member == null || member.isRemoved() || member.getLeftAt() != null)
            throw error(HttpStatus.FORBIDDEN, "Người tham gia không hợp lệ.");
    }

    private void host(Meeting meeting, User actor) {
        active(meeting, actor);
        if (!meeting.getUser().getId().equals(actor.getId()))
            throw error(HttpStatus.FORBIDDEN, "Chỉ Host hiện tại được thực hiện thao tác này.");
    }

    private void record(Meeting meeting, Long actorId, String action, Long oldHost, Long oldCoHost) {
        advanceVersion(meeting);
        history.save(new MeetingRoleHistory(meeting, actorId, action, oldHost, oldCoHost));
    }

    private void advanceVersion(Meeting meeting) {
        meeting.setRolesVersion(meeting.getRolesVersion() + 1);
        meetings.save(meeting);
    }

    private Long coHostId(Meeting meeting) {
        return meeting.getCoHost() == null ? null : meeting.getCoHost().getId();
    }

    private MeetingRolesDto view(Meeting meeting) {
        List<MeetingRolesDto.Member> members = sessions.inRoom(meeting.getCode()).stream()
                .map(MeetingRoomSessions.Connection::userId).distinct().sorted()
                .map(id -> {
                    User member = users.findById(id).orElseThrow();
                    String role = id.equals(meeting.getUser().getId()) ? "HOST"
                            : id.equals(coHostId(meeting)) ? "CO_HOST" : "PARTICIPANT";
                    String display = (Objects.toString(member.getFirstName(), "") + " "
                            + Objects.toString(member.getLastName(), "")).trim();
                    String avatar = member.getAvatar();
                    String googleAvatarUrl = "google".equals(member.getProvider()) && avatar != null
                            && avatar.matches("https://(?:[a-zA-Z0-9-]+\\.)+googleusercontent\\.com/\\S+")
                            ? avatar : null;
                    return new MeetingRolesDto.Member(id, member.getUserName(),
                            display.isBlank() ? member.getUserName() : display, role, googleAvatarUrl);
                }).toList();
        return new MeetingRolesDto(meeting.getId(), meeting.getCode(), meeting.getCreator().getId(),
                meeting.getUser().getId(), coHostId(meeting), meeting.getRolesVersion(),
                meeting.getEndTime() != null, members);
    }

    private void publish(String room) {
        MeetingRolesDto state = transactions.execute(status -> view(locked(room)));
        sessions.broadcast(room, Map.of("type", "meeting-roles-updated", "roomId", room, "state", state));
    }

    public MeetingRolesDto state(String room, String actorName) {
        synchronized (sessions.lock(room)) {
            return transactions.execute(status -> {
                Meeting meeting = locked(room);
                active(meeting, user(actorName));
                return view(meeting);
            });
        }
    }

    public User join(String room, String name, WebSocketSession socket) {
        synchronized (sessions.lock(room)) {
            User member = transactions.execute(status -> {
                Meeting meeting = locked(room);
                open(meeting);
                User actor = user(name);
                joined(meeting, actor);
                boolean returning = !sessions.online(room, actor.getId());
                if (returning && meeting.getCreator().getId().equals(actor.getId())
                        && !meeting.getUser().getId().equals(actor.getId())) {
                    Long oldHost = meeting.getUser().getId();
                    Long oldCoHost = coHostId(meeting);
                    meeting.setUser(actor);
                    if (actor.getId().equals(oldCoHost)) meeting.setCoHost(null);
                    record(meeting, actor.getId(), "CREATOR_RESTORED", oldHost, oldCoHost);
                }
                meeting.setActive(true);
                advanceVersion(meeting);
                return actor;
            });
            absences.remove(room + ":" + member.getId());
            sessions.add(room, member.getId(), member.getUserName(), socket);
            MeetingRolesDto state = transactions.execute(status -> view(locked(room)));
            scheduleAbsentRole(room, state.hostId());
            scheduleAbsentRole(room, state.coHostId());
            publish(room);
            return member;
        }
    }

    private void scheduleAbsentRole(String room, Long userId) {
        if (userId != null && !sessions.online(room, userId))
            absences.computeIfAbsent(room + ":" + userId,
                    key -> new Absence(room, userId, Instant.now().plusSeconds(30)));
    }

    public MeetingRolesDto change(String room, String actorName, String action, Long targetId, Long version) {
        synchronized (sessions.lock(room)) {
            transactions.executeWithoutResult(status -> {
                Meeting meeting = locked(room);
                User actor = user(actorName);
                active(meeting, actor);
                if (version == null || version != meeting.getRolesVersion())
                    throw error(HttpStatus.CONFLICT, "Vai trò đã thay đổi. Hãy kiểm tra danh sách và thử lại.");
                Long oldHost = meeting.getUser().getId();
                Long oldCoHost = coHostId(meeting);
                boolean isHost = oldHost.equals(actor.getId());
                if (!isHost && !actor.getId().equals(oldCoHost))
                    throw error(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý cuộc họp.");
                if ("END".equals(action) || "REVOKE_CO_HOST".equals(action)) {
                    host(meeting, actor);
                    if ("END".equals(action)) {
                        meeting.setEndTime(LocalDateTime.now());
                        meeting.setActive(false);
                        meeting.setCoHost(null);
                        for (MeetingRoomSessions.Connection c : sessions.inRoom(room))
                            markLeft(meeting, c.userId());
                    } else meeting.setCoHost(null);
                } else {
                    if (targetId == null) throw error(HttpStatus.BAD_REQUEST, "Hãy chọn người nhận.");
                    User target = users.findById(targetId).orElseThrow(() ->
                            error(HttpStatus.NOT_FOUND, "Người tham gia không tồn tại."));
                    active(meeting, target);
                    if (targetId.equals(actor.getId()) || targetId.equals(oldHost))
                        throw error(HttpStatus.BAD_REQUEST, "Không thể áp dụng thao tác với chính mình hoặc Host.");
                    if (!isHost && targetId.equals(oldCoHost))
                        throw error(HttpStatus.FORBIDDEN, "Co-Host chỉ được quản lý người tham gia thông thường.");
                    switch (action) {
                        case "TRANSFER_HOST" -> {
                            host(meeting, actor);
                            meeting.setUser(target);
                            if (targetId.equals(oldCoHost)) meeting.setCoHost(null);
                        }
                        case "SET_CO_HOST" -> {
                            host(meeting, actor);
                            meeting.setCoHost(target);
                        }
                        case "REMOVE" -> {
                            Participants removed = membership(meeting, targetId);
                            removed.setRemoved(true);
                            if (rewards != null) rewards.left(meeting, targetId);
                            removed.setLeftAt(LocalDateTime.now());
                            participants.save(removed);
                            if (targetId.equals(oldCoHost)) meeting.setCoHost(null);
                        }
                        case "MUTE", "REQUEST_UNMUTE", "STOP_SCREEN" -> { }
                        default -> throw error(HttpStatus.BAD_REQUEST, "Thao tác không hợp lệ.");
                    }
                }
                record(meeting, actor.getId(), action, oldHost, oldCoHost);
            });
            // These messages are sent only after the database transaction commits.
            if (Set.of("REMOVE", "MUTE", "REQUEST_UNMUTE", "STOP_SCREEN").contains(action)) {
                for (MeetingRoomSessions.Connection c : sessions.inRoom(room))
                    if (c.userId().equals(targetId)) sessions.send(c, Map.of("type", "meeting-command",
                            "roomId", room, "action", action, "actorName", actorName));
                if ("REMOVE".equals(action)) {
                    sessions.closeUser(room, targetId);
                    absences.remove(room + ":" + targetId);
                    sessions.broadcast(room, Map.of("type", "participant-left", "roomId", room,
                            "senderId", users.findById(targetId).orElseThrow().getUserName()));
                }
            }
            publish(room);
            if ("END".equals(action)) sessions.closeUser(room, null);
            return transactions.execute(status -> view(locked(room)));
        }
    }

    private void markLeft(Meeting meeting, Long id) {
        if (rewards != null) rewards.left(meeting, id);
        Participants member = membership(meeting, id);
        if (member != null) {
            member.setLeftAt(LocalDateTime.now());
            participants.save(member);
        }
    }

    public void leave(String room, String actorName, boolean explicit) {
        synchronized (sessions.lock(room)) {
            User actor = user(actorName);
            transactions.executeWithoutResult(status -> {
                Meeting meeting = locked(room);
                if (meeting.getEndTime() != null) { markLeft(meeting, actor.getId()); return; }
                Long oldHost = meeting.getUser().getId();
                Long oldCoHost = coHostId(meeting);
                boolean leavingHost = oldHost.equals(actor.getId());
                boolean successorOnline = oldCoHost != null && sessions.online(room, oldCoHost);
                if (explicit && leavingHost && !successorOnline
                        && sessions.inRoom(room).stream().anyMatch(c -> !c.userId().equals(actor.getId())))
                    throw error(HttpStatus.CONFLICT, "Hãy chọn Co-Host hoặc chuyển Host trước khi rời phòng.");
                if (leavingHost && successorOnline) {
                    meeting.setUser(meeting.getCoHost());
                    meeting.setCoHost(null);
                    record(meeting, actor.getId(), "HOST_SUCCEEDED", oldHost, oldCoHost);
                } else if (actor.getId().equals(oldCoHost)) {
                    meeting.setCoHost(null);
                    record(meeting, actor.getId(), "CO_HOST_LEFT", oldHost, oldCoHost);
                } else if (explicit && leavingHost) {
                    meeting.setEndTime(LocalDateTime.now());
                    meeting.setActive(false);
                    meeting.setCoHost(null);
                    record(meeting, actor.getId(), "END", oldHost, oldCoHost);
                } else {
                    advanceVersion(meeting);
                }
                markLeft(meeting, actor.getId());
            });
            absences.remove(room + ":" + actor.getId());
            sessions.closeUser(room, actor.getId());
            sessions.broadcast(room, Map.of("type", "participant-left", "roomId", room, "senderId", actorName));
            publish(room);
        }
    }

    public void disconnected(String sessionId) {
        MeetingRoomSessions.Connection c = sessions.get(sessionId);
        if (c == null) return;
        synchronized (sessions.lock(c.room())) {
            sessions.remove(sessionId);
            if (!sessions.online(c.room(), c.userId())) {
                transactions.executeWithoutResult(status -> {
                    if (rewards != null) rewards.left(locked(c.room()), c.userId());
                });
                absences.put(c.room() + ":" + c.userId(), new Absence(c.room(), c.userId(), Instant.now().plusSeconds(30)));
                sessions.broadcast(c.room(), Map.of("type", "participant-left", "roomId", c.room(), "senderId", c.userName()));
            }
            transactions.executeWithoutResult(status -> advanceVersion(locked(c.room())));
            publish(c.room());
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void expireDisconnected() {
        expireDisconnected(Instant.now());
    }

    void expireDisconnected(Instant now) {
        // Detect dead sockets even when the network never delivers a TCP close frame.
        for (MeetingRoomSessions.Connection connection : sessions.all()) {
            synchronized (sessions.lock(connection.room())) {
                Instant seen = (Instant) connection.socket().getAttributes().get("meetingLastSeen");
                if (seen != null && seen.plusSeconds(25).isBefore(now)) {
                    disconnected(connection.socket().getId());
                    try { connection.socket().close(); }
                    catch (java.io.IOException e) { log.debug("Idle socket already closed"); }
                }
            }
        }
        for (Map.Entry<String, Absence> entry : absences.entrySet()) {
            Absence absence = entry.getValue();
            if (absence.deadline().isAfter(now)) continue;
            synchronized (sessions.lock(absence.room())) {
                if (absences.get(entry.getKey()) != absence) continue;
                try {
                    if (!sessions.online(absence.room(), absence.userId()))
                        leave(absence.room(), users.findById(absence.userId()).orElseThrow().getUserName(), false);
                    absences.remove(entry.getKey(), absence);
                } catch (RuntimeException e) {
                    log.warn("Will retry disconnected participant cleanup in room {}", absence.room(), e);
                }
            }
        }
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
