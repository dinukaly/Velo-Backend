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
public class EmailVerificationService {

    private final StringRedisTemplate redisTemplate;

    // Redis key prefixes
    private static final String TOKEN_KEY_PREFIX   = "email_verify:token:";
    private static final String COOLDOWN_KEY_PREFIX = "email_verify:cooldown:";

    // TTLs
    private static final long TOKEN_TTL_HOURS      = 24;
    private static final long COOLDOWN_TTL_MINUTES = 2;

    // Token generation
    public String createVerificationToken(String userId) {
        // Rate limit check
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            log.warn("[EmailVerification] Rate limit hit for userId={}", userId);
            throw new BadRequestException(
                    "Verification email already sent. Please wait " + COOLDOWN_TTL_MINUTES + " minutes before requesting another.");
        }

        String rawToken = UUID.randomUUID().toString();

        String hashedToken = hash(rawToken);

        // Store: hash  userId
        String tokenKey = TOKEN_KEY_PREFIX + hashedToken;
        redisTemplate.opsForValue().set(tokenKey, userId, TOKEN_TTL_HOURS, TimeUnit.HOURS);

        redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_TTL_MINUTES, TimeUnit.MINUTES);

        log.info("[EmailVerification] Token created for userId={}", userId);
        return rawToken; 
    }

    
    // Validates a raw token from the email link and return the associated userId
    public String consumeToken(String rawToken) {
        String hashedToken = hash(rawToken);
        String tokenKey    = TOKEN_KEY_PREFIX + hashedToken;

        String userId = redisTemplate.opsForValue().get(tokenKey);
        if (userId == null) {
            log.warn("[EmailVerification] Token not found or expired. hash={}", hashedToken);
            throw new CustomAuthenticationException("Verification link is invalid or has expired.");
        }

        // Delete immediately
        redisTemplate.delete(tokenKey);
        log.info("[EmailVerification] Token consumed for userId={}", userId);

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
