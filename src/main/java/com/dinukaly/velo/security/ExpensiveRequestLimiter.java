package com.dinukaly.velo.security;

import com.dinukaly.velo.config.ExpensiveRequestProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpensiveRequestLimiter {
    // One atomic operation shared by all replicas using the same Redis database.
    // A denied request does not increment the counter or extend the window.
    static final DefaultRedisScript<Long> ADMIT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('SET', KEYS[1], '1', 'PX', ARGV[2])
                return 0
            end
            if count >= tonumber(ARGV[1]) then
                return math.max(1, ttl)
            end
            redis.call('INCR', KEYS[1])
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ExpensiveRequestProperties properties;

    /** Zero means admitted; positive values are retry delays in seconds. Errors fail closed. */
    public long admit(String account) {
        Long delay = redis.execute(ADMIT, List.of(key(account)),
                Integer.toString(properties.getLimit()),
                Long.toString(properties.getWindowSeconds() * 1000L));
        if (delay == null || delay < 0) throw new IllegalStateException("Admission service unavailable");
        return delay == 0 ? 0 : 1 + (delay - 1) / 1000;
    }

    static String key(String account) {
        return "velo:expensive-requests:v1:" + accountDigest(account);
    }

    static String accountDigest(String account) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Required digest unavailable");
        }
    }
}
