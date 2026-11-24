package org.example.meetingbe.controller;

import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.example.meetingbe.service.meetingAi.MeetingAiService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingAiControllerTest {
    private final IMeetingRepo meetings = mock(IMeetingRepo.class);
    private final IUserRepo users = mock(IUserRepo.class);
    private final IParticipantsRepo participants = mock(IParticipantsRepo.class);
    private final MeetingAiService ai = mock(MeetingAiService.class);
    private final MeetingAiController controller = new MeetingAiController(meetings, users, participants, ai);

    @Test void anonymousCannotCallProvider() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> controller.ask("room", new MeetingAiController.Question("test"), null)).getStatusCode().value());
        verifyNoInteractions(ai);
    }
    @Test void nonMemberAndDepartedMemberCannotCallProvider() {
        Meeting meeting = new Meeting(); meeting.setId(1L);
        User user = new User(); user.setId(2L);
        when(meetings.findByCode("room")).thenReturn(meeting);
        when(users.findByUserName("user")).thenReturn(user);
        var auth = new UsernamePasswordAuthenticationToken("user", "", List.of());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.ask("room", new MeetingAiController.Question("test"), auth)).getStatusCode().value());
        Participants member = new Participants(LocalDateTime.now(), LocalDateTime.now(), meeting, user);
        when(participants.findByUserIdAndMeetingId(2L, 1L)).thenReturn(member);
        assertThrows(ResponseStatusException.class,
                () -> controller.ask("room", new MeetingAiController.Question("test"), auth));
        verifyNoInteractions(ai);
    }
    @Test void activeMemberCanAskRegardlessOfAdminOrUserRole() {
        Meeting meeting = new Meeting(); meeting.setId(1L);
        User user = new User(); user.setId(2L);
        when(meetings.findByCode("room")).thenReturn(meeting);
        when(users.findByUserName("user")).thenReturn(user);
        when(participants.findByUserIdAndMeetingId(2L, 1L))
                .thenReturn(new Participants(LocalDateTime.now(), null, meeting, user));
        when(ai.answer("test")).thenReturn("answer");
        for (String role : List.of("ROLE_ADMIN", "ROLE_USER")) {
            var auth = new UsernamePasswordAuthenticationToken("user", "",
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role)));
            assertEquals("answer", controller.ask("room", new MeetingAiController.Question(" test "), auth).answer());
        }
    }
}
