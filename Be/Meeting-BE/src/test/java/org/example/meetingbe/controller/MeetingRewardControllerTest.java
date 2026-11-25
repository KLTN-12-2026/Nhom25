package org.example.meetingbe.controller;
import org.example.meetingbe.service.rewards.MeetingRewardService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MeetingRewardControllerTest {
    MeetingRewardService service=mock(MeetingRewardService.class);
    MeetingRewardController controller=new MeetingRewardController(service);
    @Test void anonymousCannotReadOrRedeemPoints() {
        assertThrows(RuntimeException.class,()->controller.me(null));
        assertThrows(RuntimeException.class,()->controller.redeem(null,1L));
        verifyNoInteractions(service);
    }
    @Test void userCannotUseAdminRoutes() {
        var user=new UsernamePasswordAuthenticationToken("learner","",List.of(new SimpleGrantedAuthority("USER")));
        assertThrows(RuntimeException.class,()->controller.users(user));
        assertThrows(RuntimeException.class,()->controller.rules(user));
        assertThrows(RuntimeException.class,()->controller.adjust(user,1L,new MeetingRewardController.Adjustment(100,"test")));
        verifyNoInteractions(service);
    }
    @Test void authenticatedIdentityIsUsedForOwnAccount() {
        var user=new UsernamePasswordAuthenticationToken("learner","",List.of(new SimpleGrantedAuthority("USER")));
        controller.me(user);controller.redeem(user,2L);
        verify(service).summary("learner");verify(service).redeem("learner",2L);
    }
    @Test void adminCanReadRules() {
        var admin=new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ADMIN")));
        controller.rules(admin);verify(service).rules();
    }
}
