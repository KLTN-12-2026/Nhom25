package org.example.meetingbe.service.meetingRecording;

import org.example.meetingbe.model.MeetingRecording;
import org.example.meetingbe.repository.IMeetingRecordingRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingRecordingServiceTest {
    @Mock IMeetingRecordingRepo repository;
    @InjectMocks MeetingRecordingService service;
    @Test void missingRecordingIsAnEmptyResult() {
        when(repository.findByMeetingId(1L)).thenReturn(Optional.empty());
        assertNull(service.findByMeetingId(1L));
    }
    @Test void existingRecordingIsReturned() {
        var recording = new MeetingRecording();
        when(repository.findByMeetingId(1L)).thenReturn(Optional.of(recording));
        assertSame(recording, service.findByMeetingId(1L));
    }
}
