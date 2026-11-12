package org.example.meetingbe.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private final JwtTokenProvider provider = new JwtTokenProvider();
    @Test void bearerHeaderReturnsTokenWithoutWhitespace() {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def.signature");
        assertEquals("abc.def.signature", provider.getJwtFromRequest(request));
    }
    @Test void missingOrInvalidBearerIsIgnored() {
        var request = new MockHttpServletRequest();
        assertNull(provider.getJwtFromRequest(request));
        request.addHeader("Authorization", "BearerInvalid");
        assertNull(provider.getJwtFromRequest(request));
    }
    @Test void emptyBearerIsIgnored() {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer   ");
        assertNull(provider.getJwtFromRequest(request));
    }
}
