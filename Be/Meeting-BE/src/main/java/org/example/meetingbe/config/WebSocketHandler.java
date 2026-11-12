package org.example.meetingbe.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.meetingbe.model.User;
import org.example.meetingbe.security.jwt.JwtTokenProvider;
import org.example.meetingbe.service.meeting.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    private final MeetingRoomSessions sessions;
    private final MeetingRolesService roles;
    private final JwtTokenProvider jwt;
    private final ObjectMapper mapper;

    public WebSocketHandler(MeetingRoomSessions sessions, MeetingRolesService roles,
                            JwtTokenProvider jwt, ObjectMapper mapper) {
        this.sessions = sessions;
        this.roles = roles;
        this.jwt = jwt;
        this.mapper = mapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws Exception {
        try {
            Map<String, Object> signal = mapper.readValue(message.getPayload(), new TypeReference<>() {});
            String type = (String) signal.get("type");
            MeetingRoomSessions.Connection connection = sessions.get(socket.getId());
            if (connection == null) {
                String token = (String) signal.get("token");
                if (!"new-participant".equals(type) || token == null || !jwt.validateToken(token)) {
                    reject(socket, "Vui lòng đăng nhập lại.");
                    return;
                }
                String room = (String) signal.get("roomId");
                if (room == null || room.isBlank()) { reject(socket, "Thiếu mã phòng."); return; }
                synchronized (sessions.lock(room)) {
                    User user = roles.join(room, jwt.getUsernameFromToken(token), socket);
                    socket.getAttributes().put("meetingToken", token);
                    for (MeetingRoomSessions.Connection other : sessions.inRoom(room))
                        if (!other.userId().equals(user.getId())) sessions.send(other, Map.of(
                                "type", "new-participant", "roomId", room, "senderId", user.getUserName()));
                }
                return;
            }
            synchronized (sessions.lock(connection.room())) {
                if (sessions.get(socket.getId()) == null) return;
                sessions.touch(connection);
                if (!jwt.validateToken((String) socket.getAttributes().get("meetingToken"))) {
                    reject(socket, "Phiên đăng nhập đã hết hạn.");
                    return;
                }
                if ("heartbeat".equals(type)) {
                    sessions.send(connection, Map.of("type", "heartbeat", "roomId", connection.room()));
                    return;
                }
                if (!Set.of("offer", "answer", "ice-candidate", "chat-message", "status-update").contains(type))
                    return;
                signal.remove("token");
                signal.put("roomId", connection.room());
                signal.put("senderId", connection.userName());
                if ("chat-message".equals(type) || "status-update".equals(type)) {
                    sessions.broadcast(connection.room(), signal);
                } else {
                    String target = (String) signal.get("targetParticipantId");
                    for (MeetingRoomSessions.Connection other : sessions.inRoom(connection.room()))
                        if (other.userName().equals(target)) sessions.send(other, signal);
                }
            }
        } catch (ResponseStatusException e) {
            reject(socket, e.getReason());
        } catch (Exception e) {
            log.error("Cannot process meeting signal for session {}", socket.getId(), e);
            reject(socket, "Kết nối phòng không hợp lệ. Vui lòng đăng nhập và tham gia lại.");
        }
    }

    private void reject(WebSocketSession socket, String reason) throws Exception {
        synchronized (socket) {
            if (socket.isOpen()) {
                socket.sendMessage(new TextMessage(mapper.writeValueAsString(
                        Map.of("type", "meeting-error", "message", Objects.toString(reason, "Không thể tham gia.")))));
                socket.close(CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        roles.disconnected(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        session.close(CloseStatus.SERVER_ERROR);
    }
}
