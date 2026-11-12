package org.example.meetingbe.service.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Authenticated connections on this backend instance, shared by HTTP and signaling. */
@Component
public class MeetingRoomSessions {
    private static final Logger log = LoggerFactory.getLogger(MeetingRoomSessions.class);
    public record Connection(String room, Long userId, String userName, WebSocketSession socket) {}
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, Object> roomLocks = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public MeetingRoomSessions(ObjectMapper mapper) { this.mapper = mapper; }
    public Object lock(String room) { return roomLocks.computeIfAbsent(room, key -> new Object()); }
    public Connection get(String sessionId) { return connections.get(sessionId); }

    public void add(String room, Long userId, String name, WebSocketSession socket) {
        socket.getAttributes().put("meetingLastSeen", Instant.now());
        connections.put(socket.getId(), new Connection(room, userId, name,
                new ConcurrentWebSocketSessionDecorator(socket, 10000, 1024 * 1024)));
    }

    public void remove(String sessionId) { connections.remove(sessionId); }
    public List<Connection> all() { return List.copyOf(connections.values()); }
    public void touch(Connection connection) {
        connection.socket().getAttributes().put("meetingLastSeen", Instant.now());
    }

    public boolean online(String room, Long userId) {
        return inRoom(room).stream().anyMatch(c -> c.userId().equals(userId));
    }

    public List<Connection> inRoom(String room) {
        return connections.values().stream()
                .filter(c -> c.room().equals(room) && c.socket().isOpen()).toList();
    }

    public void send(Connection connection, Object payload) {
        try {
            if (connection.socket().isOpen())
                connection.socket().sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException e) {
            log.warn("Cannot deliver room event to session {}", connection.socket().getId());
        }
    }

    public void broadcast(String room, Object payload) {
        inRoom(room).forEach(c -> send(c, payload));
    }

    public void closeUser(String room, Long userId) {
        // Remove before closing so the close callback cannot perform a second leave.
        for (Connection c : inRoom(room)) {
            if (userId == null || c.userId().equals(userId)) {
                remove(c.socket().getId());
                try { c.socket().close(CloseStatus.NORMAL); }
                catch (IOException e) { log.warn("Cannot close room session"); }
            }
        }
    }
}
