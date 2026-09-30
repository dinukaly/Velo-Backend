package com.dinukaly.velo.security;

import com.dinukaly.velo.exception.AiCallBusyException;
import com.dinukaly.velo.exception.AiCallUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class AiCallConcurrencyLimiter {
    static final long LEASE_MILLIS = 120_000;
    static final long RENEW_SECONDS = 15;

    static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then return 0 end
            return math.max(1, redis.call('PTTL', KEYS[1]))
            """, Long.class);
    static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);
    static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ScheduledExecutorService scheduler;

    public Lease acquire(String account) {
        String key = "velo:ai-call:v1:" + ExpensiveRequestLimiter.accountDigest(account);
        String token = UUID.randomUUID().toString();
        Long delay;
        try {
            delay = redis.execute(ACQUIRE, List.of(key), token, Long.toString(LEASE_MILLIS));
        } catch (RuntimeException ex) {
            throw new AiCallUnavailableException();
        }
        if (delay == null || delay < 0) throw new AiCallUnavailableException();
        if (delay > 0) throw new AiCallBusyException(1 + (delay - 1) / 1000);

        Lease lease = new Lease(key, token);
        try {
            lease.renewal = scheduler.scheduleAtFixedRate(lease::renew, RENEW_SECONDS, RENEW_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            lease.close();
            throw new AiCallUnavailableException();
        }
        return lease;
    }

    public final class Lease implements AutoCloseable {
        private final String key;
        private final String token;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean healthy = new AtomicBoolean(true);
        private ScheduledFuture<?> renewal;

        private Lease(String key, String token) {
            this.key = key;
            this.token = token;
        }

        private void renew() {
            if (closed.get()) return;
            try {
                Long result = redis.execute(RENEW, List.of(key), token, Long.toString(LEASE_MILLIS));
                if (!Long.valueOf(1).equals(result)) healthy.set(false);
            } catch (RuntimeException ex) {
                healthy.set(false);
            }
        }

        public void requireHealthy() {
            if (!healthy.get()) throw new AiCallUnavailableException();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (renewal != null) renewal.cancel(false);
            try {
                redis.execute(RELEASE, List.of(key), token);
            } catch (RuntimeException ignored) {
                // Expiry is the fallback after a failed release.
            }
        }
    }
}
