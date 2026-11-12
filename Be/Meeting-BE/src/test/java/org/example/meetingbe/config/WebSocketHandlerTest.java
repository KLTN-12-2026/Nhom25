package org.example.meetingbe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.meetingbe.security.jwt.JwtTokenProvider;
import org.example.meetingbe.service.meeting.*;
import org.junit.jupiter.api.*;
import org.springframework.web.socket.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSocketHandlerTest {
    private MeetingRoomSessions sessions;
    private MeetingRolesService roles;
    private WebSocketHandler handler;
    private WebSocketSession sender;
    private WebSocketSession receiver;

    @BeforeEach
    void setup() throws Exception {
        sessions = new MeetingRoomSessions(new ObjectMapper());
        roles = mock(MeetingRolesService.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        when(jwt.validateToken("valid")).thenReturn(true);
        handler = new WebSocketHandler(sessions, roles, jwt, new ObjectMapper());
        sender = socket("sender");
        receiver = socket("receiver");
        sender.getAttributes().put("meetingToken", "valid");
    }

    private WebSocketSession socket(String id) {
        WebSocketSession value = mock(WebSocketSession.class);
        when(value.getId()).thenReturn(id);
        when(value.isOpen()).thenReturn(true);
        when(value.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        return value;
    }

    @Test
    void unauthenticatedConnectionCannotJoin() throws Exception {
        handler.handleTextMessage(sender, new TextMessage(
                "{\"type\":\"new-participant\",\"roomId\":\"room\",\"senderId\":\"host\"}"));
        verifyNoInteractions(roles);
        verify(sender).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void forgedSenderAndRoomAreReplacedByAuthenticatedConnection() throws Exception {
        sessions.add("room", 1L, "actual-user", sender);
        sessions.add("room", 2L, "receiver", receiver);
        handler.handleTextMessage(sender, new TextMessage(
                "{\"type\":\"status-update\",\"roomId\":\"other-room\",\"senderId\":\"host\"}"));
        verify(receiver).sendMessage(argThat(message -> {
            String json = message.getPayload().toString();
            return json.contains("actual-user") && json.contains("\"roomId\":\"room\"")
                    && !json.contains("other-room");
        }));
    }

    @Test
    void clientCannotForgeServerModerationEvents() throws Exception {
        sessions.add("room", 1L, "member", sender);
        sessions.add("room", 2L, "receiver", receiver);
        handler.handleTextMessage(sender, new TextMessage(
                "{\"type\":\"meeting-command\",\"action\":\"REMOVE\",\"targetParticipantId\":\"receiver\"}"));
        handler.handleTextMessage(sender, new TextMessage(
                "{\"type\":\"meeting-roles-updated\",\"roomId\":\"room\"}"));
        verify(receiver, never()).sendMessage(any());
        verifyNoInteractions(roles);
    }
}
