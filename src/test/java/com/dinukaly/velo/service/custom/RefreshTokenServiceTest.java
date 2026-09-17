package com.dinukaly.velo.service.custom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        refreshTokenService = new RefreshTokenService(redisTemplate);
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 604_800_000L);
    }

    @Test
    void refreshTokenTtlUsesConfiguredMilliseconds() {
        String email = "user@example.com";

        refreshTokenService.createRefreshToken(email);

        verify(valueOperations).set(
                anyString(),
                eq(email),
                eq(604_800_000L),
                eq(TimeUnit.MILLISECONDS));
    }
}
