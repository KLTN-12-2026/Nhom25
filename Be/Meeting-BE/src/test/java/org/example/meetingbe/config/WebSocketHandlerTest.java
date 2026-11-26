package org.example.meetingbe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.meetingbe.model.ChatMessage;
import java.time.LocalDateTime;
import org.example.meetingbe.security.jwt.JwtTokenProvider;
import org.example.meetingbe.service.chatMessage.ChatMessageService;
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
    private ChatMessageService chat;
    private WebSocketSession sender;
    private WebSocketSession receiver;

    @BeforeEach
    void setup() throws Exception {
        sessions = new MeetingRoomSessions(new ObjectMapper());
        roles = mock(MeetingRolesService.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        when(jwt.validateToken("valid")).thenReturn(true);
        chat = mock(ChatMessageService.class);
        handler = new WebSocketHandler(sessions, roles, jwt, new ObjectMapper(), chat);
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

    @Test
    void persistsChatUnderAuthenticatedRoomAndSenderBeforeBroadcast() throws Exception {
        sessions.add("room", 1L, "actual-user", sender);
        sessions.add("room", 2L, "receiver", receiver);
        ChatMessage saved = new ChatMessage("room", "actual-user", "hello",
                LocalDateTime.of(2026, 11, 26, 12, 0));
        saved.setId(17L);
        when(chat.saveChatMessage("room", "actual-user", "hello")).thenReturn(saved);

        handler.handleTextMessage(sender, new TextMessage(
                "{\"type\":\"chat-message\",\"roomId\":\"other-room\",\"senderId\":\"host\",\"message\":\"hello\"}"));

        var ordered = inOrder(chat, receiver);
        ordered.verify(chat).saveChatMessage("room", "actual-user", "hello");
        ordered.verify(receiver).sendMessage(argThat(message -> {
            String json = message.getPayload().toString();
            return json.contains("\"id\":17") && json.contains("\"senderId\":\"actual-user\"")
                    && json.contains("\"roomId\":\"room\"") && json.contains("2026-11-26T12:00");
        }));
    }

}
