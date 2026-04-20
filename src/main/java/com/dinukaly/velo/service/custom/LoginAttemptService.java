package com.dinukaly.velo.service.custom;

import com.dinukaly.velo.exception.CustomAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Tracks failed login attempts per email in Redis and enforces a
 * temporary lockout after a configurable number of failures
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "login_attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    /**
     * Call this before validating credentials
     */
    public void checkBlocked(String email) {
        String key = KEY_PREFIX + email.toLowerCase();
        String countStr = redisTemplate.opsForValue().get(key);
        if (countStr != null && Integer.parseInt(countStr) >= MAX_ATTEMPTS) {
            log.warn("[LoginAttempt] Account locked: {}", email);
            throw new CustomAuthenticationException(
                    "Account temporarily locked due to too many failed attempts. Try again in " + LOCKOUT_MINUTES + " minutes.");
        }
    }

    /**
     * Increments the counter; sets/renews 15-minute TTL
     */
    public void recordFailure(String email) {
        String key = KEY_PREFIX + email.toLowerCase();
        Long count = redisTemplate.opsForValue().increment(key);
        //only set TTL on first failure to avoid extending the window on each attempt
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCKOUT_MINUTES, TimeUnit.MINUTES);
        }
        log.warn("[LoginAttempt] Failed attempt #{} for: {}", count, email);
    }

    /**
     * clears the failure counter immediately
     */
    public void clearFailures(String email) {
        redisTemplate.delete(KEY_PREFIX + email.toLowerCase());
    }
}
