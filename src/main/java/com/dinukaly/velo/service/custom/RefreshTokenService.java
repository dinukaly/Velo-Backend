package com.dinukaly.velo.service.custom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpirationSeconds;

    // Redis key prefix
    private static final String KEY_PREFIX = "refresh_token:";

    /**
     * Generates a new UUID refresh token, hashes it, and stores it in Redis
     */
    public String createRefreshToken(String email) {
        String rawToken = UUID.randomUUID().toString();
        String hashed = hash(rawToken);
        redisTemplate.opsForValue().set(KEY_PREFIX + hashed, email, refreshExpirationSeconds, TimeUnit.SECONDS);
        log.debug("[RefreshTokenService] Created refresh token for: {}", email);
        return rawToken;
    }

    public String getEmailByToken(String rawToken) {
        String hashed = hash(rawToken);
        return redisTemplate.opsForValue().get(KEY_PREFIX + hashed);
    }

    public String rotateRefreshToken(String oldRawToken, String email) {
        // delete old token
        String oldHashed = hash(oldRawToken);
        redisTemplate.delete(KEY_PREFIX + oldHashed);
        // issue a new one
        return createRefreshToken(email);
    }

    public void revokeToken(String rawToken) {
        String hashed = hash(rawToken);
        redisTemplate.delete(KEY_PREFIX + hashed);
        log.info("[RefreshTokenService] Revoked refresh token");
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
