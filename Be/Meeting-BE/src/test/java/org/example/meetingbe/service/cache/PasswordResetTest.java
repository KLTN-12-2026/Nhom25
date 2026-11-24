package org.example.meetingbe.service.cache;

import org.example.meetingbe.controller.UserController;
import org.example.meetingbe.service.user.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetTest {
    private CacheService cache() {
        return new CacheService();
    }

    @Test
    void otpVerificationAuthorizesOnlyOneResetForSameEmail() {
        CacheService cache = cache();
        IUserService users = mock(IUserService.class);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "cacheService", cache);
        ReflectionTestUtils.setField(controller, "userService", users);
        String email = "member@example.com";
        String otp = cache.generateOtp(email);
        assertEquals(400, controller.verifyOtp(Map.of("email", email, "otp", "wrong")).getStatusCode().value());
        assertEquals(401, controller.resetPassword(Map.of("email", email, "password", "newpass123")).getStatusCode().value());
        var verified = controller.verifyOtp(Map.of("email", email, "otp", otp));
        assertEquals(200, verified.getStatusCode().value());
        String token = (String) ((Map<?, ?>) verified.getBody()).get("resetToken");
        assertEquals(400, controller.verifyOtp(Map.of("email", email, "otp", otp)).getStatusCode().value());
        assertFalse(cache.consumeResetToken("other@example.com", token));
        var request = Map.of("email", email, "password", "newpass123", "resetToken", token);
        assertEquals(200, controller.resetPassword(request).getStatusCode().value());
        assertEquals(401, controller.resetPassword(request).getStatusCode().value());
        verify(users, times(1)).resetPassword("newpass123", email);
    }

    @Test
    void sendingNewOtpInvalidatesPreviousResetPermission() {
        CacheService cache = cache();
        String token = cache.issueResetToken("member@example.com");
        cache.generateOtp("member@example.com");
        assertFalse(cache.consumeResetToken("member@example.com", token));
    }
}
