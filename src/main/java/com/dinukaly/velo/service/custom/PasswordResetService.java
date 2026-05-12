package com.dinukaly.velo.service.custom;

import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_KEY_PREFIX = "password_reset:token:";
    private static final String COOLDOWN_KEY_PREFIX = "password_reset:cooldown:";

    private static final long TOKEN_TTL_MINUTES = 60;
    private static final long COOLDOWN_TTL_MINUTES = 2;

    public String createResetToken(String userId) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            log.warn("[PasswordReset] Rate limit hit for userId={}", userId);
            throw new BadRequestException(
                    "A reset email was already sent recently. Please wait " + COOLDOWN_TTL_MINUTES + " minutes before requesting another.");
        }

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hash(rawToken);

        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + hashedToken,
                userId,
                TOKEN_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        redisTemplate.opsForValue().set(
                cooldownKey,
                "1",
                COOLDOWN_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        log.info("[PasswordReset] Token created for userId={}", userId);
        return rawToken;
    }

    public String consumeToken(String rawToken) {
        String hashedToken = hash(rawToken);
        String tokenKey = TOKEN_KEY_PREFIX + hashedToken;

        String userId = redisTemplate.opsForValue().get(tokenKey);
        if (userId == null) {
            log.warn("[PasswordReset] Token not found or expired. hash={}", hashedToken);
            throw new CustomAuthenticationException("Password reset link is invalid or has expired.");
        }

        redisTemplate.delete(tokenKey);
        log.info("[PasswordReset] Token consumed for userId={}", userId);
        return userId;
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
