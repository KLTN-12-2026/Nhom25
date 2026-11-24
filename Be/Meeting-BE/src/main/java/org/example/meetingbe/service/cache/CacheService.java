package org.example.meetingbe.service.cache;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class CacheService {
    private final Cache<String, String> otps = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1000).build();
    private final Cache<String, String> resetTokens = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1000).build();
    public String generateOtp(@Param("email") String email) {
        resetTokens.invalidate(email);
        String otp = String.valueOf(new SecureRandom().nextInt(9000) + 1000);
        otps.put(email, otp);
        return otp;
    }

    public String getOtp(@Param("email") String email) {
        return otps.getIfPresent(email);
    }

    public void clearOtp(@Param("email") String email) {
        otps.invalidate(email);
    }
    public String issueResetToken(String email) {
        String token = UUID.randomUUID().toString();
        resetTokens.put(email, token);
        return token;
    }
    public boolean consumeResetToken(String email, String token) {
        return token != null && resetTokens.asMap().remove(email, token);
    }
}
