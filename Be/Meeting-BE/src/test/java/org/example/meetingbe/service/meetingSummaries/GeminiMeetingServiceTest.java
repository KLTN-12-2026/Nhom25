package org.example.meetingbe.service.meetingSummaries;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class GeminiMeetingServiceTest {
    private final GeminiMeetingService service = new GeminiMeetingService(new ObjectMapper(), "", "test-model");
    @Test void providerOverloadHasActionableMessage() {
        var error = assertThrows(ResponseStatusException.class, () -> service.checkAvailability(503));
        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("quá tải"));
        assertDoesNotThrow(() -> service.checkAvailability(200));
    }
    @Test void missingKeyFailsBeforeAnyNetworkRequest() {
        var error = assertThrows(ResponseStatusException.class, () -> service.summarize(new byte[]{1}, "audio/wav"));
        assertEquals(503, error.getStatusCode().value());
    }
    @Test void structuredResultPreservesVietnamese() throws Exception {
        var result = service.parseResult("{\"summary\":\"Quyết định: ra mắt thứ hai\",\"transcript\":\"Chúng ta ra mắt thứ hai.\"}");
        assertEquals("Quyết định: ra mắt thứ hai", result.summary());
        assertEquals("Chúng ta ra mắt thứ hai.", result.transcript());
    }
    @Test void missingTranscriptIsNotSavedAsSuccess() {
        assertThrows(java.io.IOException.class, () -> service.parseResult("{\"summary\":\"x\"}"));
    }
    @Test void silenceIsRejected() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.parseResult("{\"summary\":\"Không có lời nói\",\"transcript\":\"\"}"));
        assertEquals(422, error.getStatusCode().value());
    }
}
