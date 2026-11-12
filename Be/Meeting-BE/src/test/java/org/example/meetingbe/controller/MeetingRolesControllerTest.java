package org.example.meetingbe.controller;

import org.example.meetingbe.service.meeting.MeetingRolesService;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MeetingRolesControllerTest {
    private MeetingRolesService roles;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        roles = mock(MeetingRolesService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MeetingRolesController(roles)).build();
    }

    @Test
    void anonymousRequestCannotPerformAction() throws Exception {
        mvc.perform(post("/api/meetings/room/actions/TRANSFER_HOST")
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":2,\"version\":0}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(roles);
    }

    @Test
    void actorComesFromAuthenticationRatherThanRequestBody() throws Exception {
        when(roles.change("room", "actual-user", "TRANSFER_HOST", 2L, 0L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Host."));
        mvc.perform(post("/api/meetings/room/actions/TRANSFER_HOST")
                .principal(new UsernamePasswordAuthenticationToken("actual-user", "", List.of()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":2,\"version\":0,\"actorName\":\"host\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Chỉ Host."));
        verify(roles).change("room", "actual-user", "TRANSFER_HOST", 2L, 0L);
    }
}
