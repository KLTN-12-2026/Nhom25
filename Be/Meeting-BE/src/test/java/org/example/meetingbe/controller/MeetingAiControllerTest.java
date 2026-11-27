package org.example.meetingbe.controller;

import org.example.meetingbe.model.*;
import org.example.meetingbe.repository.*;
import org.example.meetingbe.service.meetingAi.MeetingAiService;
import org.example.meetingbe.service.meetingAi.MeetingNoteSearch;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingAiControllerTest {
    private final IMeetingRepo meetings = mock(IMeetingRepo.class);
    private final IUserRepo users = mock(IUserRepo.class);
    private final IParticipantsRepo participants = mock(IParticipantsRepo.class);
    private final MeetingNoteRepository notes = mock(MeetingNoteRepository.class);
    private final MeetingAiService ai = mock(MeetingAiService.class);
    private final MeetingAiController controller = new MeetingAiController(meetings, users, participants, notes, ai);

    @Test void anonymousCannotCallProvider() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> controller.ask("room", new MeetingAiController.Question("test"), null)).getStatusCode().value());
        verifyNoInteractions(notes, ai);
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
        verifyNoInteractions(notes, ai);
    }
    @Test void activeMemberCanAskRegardlessOfAdminOrUserRole() {
        Meeting meeting = new Meeting(); meeting.setId(1L);
        User user = new User(); user.setId(2L);
        when(meetings.findByCode("room")).thenReturn(meeting);
        when(users.findByUserName("user")).thenReturn(user);
        when(participants.findByUserIdAndMeetingId(2L, 1L))
                .thenReturn(new Participants(LocalDateTime.now(), null, meeting, user));
        when(ai.answer("test", "", List.of())).thenReturn("answer");
        for (String role : List.of("ROLE_ADMIN", "ROLE_USER")) {
            var auth = new UsernamePasswordAuthenticationToken("user", "",
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role)));
            assertEquals("answer", controller.ask("room", new MeetingAiController.Question(" test "), auth).answer());
        }
        MeetingNote note = new MeetingNote(meeting, user);
        note.setContent("Ngô Quyền đánh tan quân Nam Hán vào năm 938.");
        when(notes.findFirstByMeetingIdAndAuthorIdOrderByIdAsc(1L, 2L)).thenReturn(Optional.of(note));
        String question = "vua Ngo Quyen danh tan quan Nam Han vao nam nao";
        when(ai.answer(question, note.getContent(), List.of())).thenReturn("Theo ghi chú của bạn, năm 938.");
        var auth = new UsernamePasswordAuthenticationToken("user", "", List.of());
        assertEquals("Theo ghi chú của bạn, năm 938.",
                controller.ask("room", new MeetingAiController.Question(question), auth).answer());

        var previous = mock(MeetingNoteRepository.HistoryNote.class);
        when(previous.getTitle()).thenReturn("Lịch sử");
        when(previous.getContent()).thenReturn(note.getContent());
        var unrelated = mock(MeetingNoteRepository.HistoryNote.class);
        when(unrelated.getContent()).thenReturn("Nhóm thống nhất đổi lịch sang thứ hai.");
        when(notes.findFirstByMeetingIdAndAuthorIdOrderByIdAsc(1L, 2L)).thenReturn(Optional.empty());
        when(notes.findPersonalHistory(2L, 0L)).thenReturn(List.of(unrelated, previous));
        when(ai.answer(question, "", List.of(new MeetingNoteSearch.Excerpt("Lịch sử", note.getContent()))))
                .thenReturn("Theo ghi chú của bạn trong cuộc họp Lịch sử, năm 938.");
        assertEquals("Theo ghi chú của bạn trong cuộc họp Lịch sử, năm 938.",
                controller.ask("room", new MeetingAiController.Question(question), auth).answer());
    }
}
