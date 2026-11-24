package org.example.meetingbe.security.userpricipal;

import org.example.meetingbe.model.User;
import org.example.meetingbe.repository.IUserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {
    @Mock
    private IUserRepo userRepo;
    @InjectMocks
    private CustomUserDetailsService service;

    private User account() {
        User user = new User();
        user.setId(1L);
        user.setUserName("member123");
        user.setEmail("member@example.com");
        user.setPassword("encoded-password");
        return user;
    }

    @Test
    void emailLoginKeepsCanonicalUsernameAndStoredPassword() {
        when(userRepo.findByEmail("member@example.com")).thenReturn(Optional.of(account()));
        var details = service.loadUserByUsername("member@example.com");
        assertEquals("member123", details.getUsername());
        assertEquals("encoded-password", details.getPassword());
    }

    @Test
    void usernameLoginStillWorks() {
        when(userRepo.findByUserName("member123")).thenReturn(account());
        assertEquals("member123", service.loadUserByUsername("member123").getUsername());
        verify(userRepo, never()).findByEmail(anyString());
    }

    @Test
    void missingAccountThrowsAuthenticationException() {
        when(userRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@example.com"));
    }
}
