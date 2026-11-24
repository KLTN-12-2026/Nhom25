package org.example.meetingbe.service.meetingAi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class MeetingAiServiceTest {
    private final MeetingAiService service = new MeetingAiService(new ObjectMapper(), "", "test",
            new ByteArrayResource(new byte[0]));
    MeetingAiServiceTest() throws IOException {}

    @Test void outOfScopeNeverReturnsGeneratedAnswer() throws Exception {
        assertEquals(MeetingAiService.REFUSAL,
                service.parseAnswer("{\"inScope\":false,\"answer\":\"unrelated answer\"}"));
    }
    @Test void inScopeReturnsAnswer() throws Exception {
        assertEquals("Bật mic", service.parseAnswer("{\"inScope\":true,\"answer\":\"Bật mic\"}"));
    }
    @Test void invalidOrAmbiguousOutputIsRejected() {
        for (String output : new String[]{"null", "{}", "{\"inScope\":\"true\",\"answer\":\"x\"}",
                "{\"inScope\":true,\"answer\":\" \"}", "not json"}) {
            assertThrows(IOException.class, () -> service.parseAnswer(output));
        }
    }
}
