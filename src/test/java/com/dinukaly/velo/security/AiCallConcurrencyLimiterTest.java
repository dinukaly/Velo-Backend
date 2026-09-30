package com.dinukaly.velo.security;

import com.dinukaly.velo.exception.AiCallBusyException;
import com.dinukaly.velo.exception.AiCallUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCallConcurrencyLimiterTest {
    @Test
    void contentionReturnsRetryWithoutStartingHeartbeat() {
        var redis = mock(StringRedisTemplate.class);
        var scheduler = mock(ScheduledExecutorService.class);
        String key = "velo:ai-call:v1:" + ExpensiveRequestLimiter.accountDigest("account");
        when(redis.execute(eq(AiCallConcurrencyLimiter.ACQUIRE), eq(List.of(key)), anyString(), eq("120000")))
                .thenReturn(1501L);
        var failure = assertThrows(AiCallBusyException.class,
                () -> new AiCallConcurrencyLimiter(redis, scheduler).acquire("account"));
        assertEquals(2, failure.getRetrySeconds());
        verifyNoInteractions(scheduler);
        assertFalse(key.contains("account"));
    }

    @Test
    void leaseRenewsAndReleasesOnlyWithItsToken() {
        var redis = mock(StringRedisTemplate.class);
        var scheduler = mock(ScheduledExecutorService.class);
        var future = mock(ScheduledFuture.class);
        String key = "velo:ai-call:v1:" + ExpensiveRequestLimiter.accountDigest("account");
        when(redis.execute(eq(AiCallConcurrencyLimiter.ACQUIRE), eq(List.of(key)), anyString(), eq("120000")))
                .thenReturn(0L);
        when(redis.execute(eq(AiCallConcurrencyLimiter.RENEW), eq(List.of(key)), anyString(), eq("120000")))
                .thenReturn(1L);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(15L), eq(15L), eq(TimeUnit.SECONDS)))
                .thenReturn(future);
        var limiter = new AiCallConcurrencyLimiter(redis, scheduler);
        var lease = limiter.acquire("account");
        var renewal = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(renewal.capture(), eq(15L), eq(15L), eq(TimeUnit.SECONDS));
        renewal.getValue().run();
        lease.requireHealthy();
        lease.close();
        lease.close();
        verify(future).cancel(false);
        var acquiredToken = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(eq(AiCallConcurrencyLimiter.ACQUIRE), eq(List.of(key)),
                acquiredToken.capture(), eq("120000"));
        verify(redis).execute(AiCallConcurrencyLimiter.RENEW, List.of(key), acquiredToken.getValue(), "120000");
        verify(redis).execute(AiCallConcurrencyLimiter.RELEASE, List.of(key), acquiredToken.getValue());
        renewal.getValue().run();
        verify(redis, times(1)).execute(eq(AiCallConcurrencyLimiter.RENEW), eq(List.of(key)), anyString(), eq("120000"));
    }

    @Test
    void redisFailureOrLostLeaseFailsClosed() {
        var redis = mock(StringRedisTemplate.class);
        var scheduler = mock(ScheduledExecutorService.class);
        when(redis.execute(eq(AiCallConcurrencyLimiter.ACQUIRE), anyList(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("private connection details"))
                .thenReturn(0L);
        var limiter = new AiCallConcurrencyLimiter(redis, scheduler);
        var failure = assertThrows(AiCallUnavailableException.class, () -> limiter.acquire("account"));
        assertFalse(failure.getMessage().contains("private connection details"));
        var future = mock(ScheduledFuture.class);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(future);
        when(redis.execute(eq(AiCallConcurrencyLimiter.RENEW), anyList(), anyString(), anyString()))
                .thenReturn(0L);
        var lease = limiter.acquire("account");
        var renewal = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(renewal.capture(), anyLong(), anyLong(), eq(TimeUnit.SECONDS));
        renewal.getValue().run();
        assertThrows(AiCallUnavailableException.class, lease::requireHealthy);
        lease.close();
    }

    @Test
    void schedulerFailureReleasesAcquiredLease() {
        var redis = mock(StringRedisTemplate.class);
        var scheduler = mock(ScheduledExecutorService.class);
        when(redis.execute(eq(AiCallConcurrencyLimiter.ACQUIRE), anyList(), anyString(), anyString()))
                .thenReturn(0L);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new IllegalStateException("shutdown"));
        assertThrows(AiCallUnavailableException.class,
                () -> new AiCallConcurrencyLimiter(redis, scheduler).acquire("account"));
        verify(redis).execute(eq(AiCallConcurrencyLimiter.RELEASE), anyList(), anyString());
    }
}
