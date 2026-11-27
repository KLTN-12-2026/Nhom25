package org.example.meetingbe.service.meeting;

import com.fasterxml.jackson.databind.*;
import org.example.meetingbe.model.User;
import org.example.meetingbe.repository.IUserRepo;
import org.example.meetingbe.security.jwt.JwtTokenProvider;
import org.example.meetingbe.security.userpricipal.UserPrinciple;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real HTTP, JWT, WebSocket serialization and MySQL using disposable fixture rows. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MeetingRoomIntegrationTest {
    @LocalServerPort int port;
    @Autowired IUserRepo users;
    @Autowired JwtTokenProvider tokens;
    @Autowired MeetingRoomSessions sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final List<User> fixtures = new ArrayList<>();
    private Long meetingId;
    private String room;

    @AfterEach
    void cleanup() {
        if (room != null) sessions.closeUser(room, null);
        if (meetingId != null) {
            jdbc.update("delete from meeting_note where meeting_id = ?", meetingId);
            jdbc.update("delete from meeting_role_history where meeting_id = ?", meetingId);
            jdbc.update("delete from participants where meeting_id = ?", meetingId);
            jdbc.update("delete from meeting where id = ?", meetingId);
        }
        fixtures.forEach(user -> users.deleteById(user.getId()));
    }

    private User account() {
        User user = new User();
        user.setUserName("pb10_" + UUID.randomUUID().toString().substring(0, 12));
        user.setPassword("fixture-not-used-for-login");
        user.setEmail(user.getUserName() + "@example.test");
        User saved = users.saveAndFlush(user);
        fixtures.add(saved);
        return saved;
    }

    private String token(User user) {
        return tokens.generateToken(new UsernamePasswordAuthenticationToken(new UserPrinciple(user), null, List.of()));
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String path(String suffix) { return "/api/meetings/" + room + suffix; }

    private RoomClient join(User user) throws Exception {
        RoomClient client = new RoomClient();
        client.socket = http.newWebSocketBuilder().header("Origin", "http://localhost:4200")
                .buildAsync(URI.create("ws://localhost:" + port + "/signaling"), client).get(10, TimeUnit.SECONDS);
        client.socket.sendText(mapper.writeValueAsString(Map.of("type", "new-participant",
                "roomId", room, "senderId", "forged-name", "token", token(user))), true).join();
        return client;
    }

    private JsonNode state(User actor) throws Exception {
        HttpResponse<String> response = request("GET", path("/roles"), token(actor), null);
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> action(User actor, String action, User target, long version) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("targetUserId", target == null ? null : target.getId());
        return request("POST", path("/actions/" + action), token(actor), body);
    }

    @Test
    void personalNoteCanBeCreatedUpdatedAndRead() throws Exception {
        User creator = account();
        String authorization = token(creator);
        HttpResponse<String> created = request("POST", "/api/meetings", authorization,
                Map.of("title", "Meeting note integration fixture"));
        assertEquals(200, created.statusCode(), created.body());
        JsonNode createdRoom = mapper.readTree(created.body());
        meetingId = createdRoom.path("id").asLong();
        room = createdRoom.path("code").asText();

        HttpResponse<String> saved = request("PUT", path("/note"), authorization, Map.of("content", "First note"));
        assertEquals(200, saved.statusCode(), saved.body());
        String updatedContent = "Ghi chú đã cập nhật";
        HttpResponse<String> updated = request("PUT", path("/note"), authorization, Map.of("content", updatedContent));
        assertEquals(200, updated.statusCode(), updated.body());

        HttpResponse<String> loaded = request("GET", path("/note"), authorization, null);
        assertEquals(200, loaded.statusCode(), loaded.body());
        assertEquals(updatedContent, mapper.readTree(loaded.body()).path("content").asText());

        HttpResponse<String> added = request("POST", path("/note/entries"), authorization, Map.of("content", "Ghi chú thứ hai"));
        assertEquals(200, added.statusCode(), added.body());
        long secondId = mapper.readTree(added.body()).path("id").asLong();
        HttpResponse<String> edited = request("PUT", path("/note/entries/" + secondId), authorization,
                Map.of("content", "Ghi chú thứ hai đã sửa"));
        assertEquals(200, edited.statusCode(), edited.body());
        HttpResponse<String> listed = request("GET", path("/note/entries"), authorization, null);
        assertEquals(200, listed.statusCode(), listed.body());
        JsonNode entries = mapper.readTree(listed.body());
        assertEquals(2, entries.size());
        assertEquals(updatedContent, entries.get(0).path("content").asText());
        assertEquals("Ghi chú thứ hai đã sửa", entries.get(1).path("content").asText());
        HttpResponse<String> exported = request("GET", path("/note/export"), authorization, null);
        assertEquals(200, exported.statusCode(), exported.body());
        assertTrue(exported.body().contains(updatedContent));
        assertTrue(exported.body().contains("Ghi chú thứ hai đã sửa"));
    }

    @Test
    void participantSnapshotsAndRoleChangesWorkThroughRealHttpAndWebSocket() throws Exception {
        User creator = account();
        User second = account();
        User third = account();
        HttpResponse<String> created = request("POST", "/api/meetings", token(creator),
                Map.of("title", "PB10 integration fixture", "hostId", third.getId()));
        assertEquals(200, created.statusCode(), created.body());
        JsonNode createdRoom = mapper.readTree(created.body());
        meetingId = createdRoom.path("id").asLong();
        room = createdRoom.path("code").asText();

        RoomClient host = join(creator);
        JsonNode initial = host.await("meeting-roles-updated").path("state");
        assertEquals(1, initial.path("participants").size());
        assertEquals(creator.getId().longValue(), initial.path("hostId").asLong());
        assertEquals(creator.getUserName(), initial.path("participants").get(0).path("userName").asText());
        assertEquals("HOST", initial.path("participants").get(0).path("role").asText());

        RoomClient cohost = join(second);
        assertEquals(2, cohost.await("meeting-roles-updated").path("state").path("participants").size());
        RoomClient participant = join(third);
        assertEquals(3, participant.await("meeting-roles-updated").path("state").path("participants").size());
        assertEquals(3, state(creator).path("participants").size());

        assertEquals(401, request("GET", path("/roles"), null, null).statusCode());
        long version = state(creator).path("version").asLong();
        assertEquals(403, action(third, "TRANSFER_HOST", second, version).statusCode());
        assertEquals(200, action(creator, "SET_CO_HOST", second, version).statusCode());
        version = state(creator).path("version").asLong();
        assertEquals(second.getId().longValue(), state(second).path("coHostId").asLong());
        assertEquals(403, action(second, "END", null, version).statusCode());
        assertEquals(200, action(second, "MUTE", third, version).statusCode());
        assertEquals("MUTE", participant.await("meeting-command").path("action").asText());

        assertEquals(204, request("POST", path("/leave"), token(creator), Map.of()).statusCode());
        JsonNode inherited = state(second);
        assertEquals(second.getId().longValue(), inherited.path("hostId").asLong());
        assertTrue(inherited.path("coHostId").isNull());
        assertEquals(2, inherited.path("participants").size());

        RoomClient returnedCreator = join(creator);
        JsonNode restored = returnedCreator.await("meeting-roles-updated").path("state");
        assertEquals(creator.getId().longValue(), restored.path("hostId").asLong());
        assertEquals(200, action(creator, "TRANSFER_HOST", second, restored.path("version").asLong()).statusCode());
        version = state(second).path("version").asLong();
        assertEquals(409, action(second, "TRANSFER_HOST", third, version - 1).statusCode());
        assertEquals(200, action(second, "REMOVE", third, version).statusCode());
        assertEquals("REMOVE", participant.await("meeting-command").path("action").asText());
        RoomClient removed = join(third);
        assertTrue(removed.await("meeting-error").path("message").asText().contains("xóa"));
        assertEquals(2, state(second).path("participants").size());
        assertEquals(200, action(second, "END", null, state(second).path("version").asLong()).statusCode());
        assertTrue(returnedCreator.awaitEnded().path("ended").asBoolean());
    }

    private class RoomClient implements WebSocket.Listener {
        private WebSocket socket;
        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final StringBuilder text = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                try { messages.add(mapper.readTree(text.toString())); }
                catch (Exception e) { throw new CompletionException(e); }
                text.setLength(0);
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }
        JsonNode await(String type) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                JsonNode event = messages.poll(1, TimeUnit.SECONDS);
                if (event == null) continue;
                if (type.equals(event.path("type").asText())) return event;
                if ("meeting-error".equals(event.path("type").asText())) fail(event.toString());
            }
            throw new AssertionError("No " + type + " received over WebSocket");
        }
        JsonNode awaitEnded() throws Exception {
            for (int i = 0; i < 10; i++) {
                JsonNode state = await("meeting-roles-updated").path("state");
                if (state.path("ended").asBoolean()) return state;
            }
            throw new AssertionError("No ended state received");
        }
    }
}
