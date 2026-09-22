package com.dinukaly.velo.security;

import com.dinukaly.velo.config.ExpensiveRequestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpensiveRequestLimiterTest {
    @Test
    void usesOneAtomicScriptWithStablePseudonymousAccountKeyAndConfiguredBudget() {
        var redis = mock(StringRedisTemplate.class);
        var properties = new ExpensiveRequestProperties();
        properties.setLimit(7);
        properties.setWindowSeconds(30);
        String key = ExpensiveRequestLimiter.key("user@example.test");
        when(redis.execute(ExpensiveRequestLimiter.ADMIT, List.of(key), "7", "30000")).thenReturn(0L);
        assertEquals(0, new ExpensiveRequestLimiter(redis, properties).admit("user@example.test"));
        verify(redis).execute(ExpensiveRequestLimiter.ADMIT, List.of(key), "7", "30000");
        assertFalse(key.contains("user@example.test"));
        assertNotEquals(key, ExpensiveRequestLimiter.key("other@example.test"));
        verifyNoMoreInteractions(redis);
    }

    @Test
    void roundsPositiveRetryDelaysUpAndFailsClosedOnInvalidResults() {
        var redis = mock(StringRedisTemplate.class);
        var limiter = new ExpensiveRequestLimiter(redis, new ExpensiveRequestProperties());
        when(redis.execute(ExpensiveRequestLimiter.ADMIT, List.of(ExpensiveRequestLimiter.key("account")),
                "20", "60000")).thenReturn(1L, 1000L, 1001L, null, -1L);
        assertEquals(1, limiter.admit("account"));
        assertEquals(1, limiter.admit("account"));
        assertEquals(2, limiter.admit("account"));
        assertThrows(IllegalStateException.class, () -> limiter.admit("account"));
        assertThrows(IllegalStateException.class, () -> limiter.admit("account"));
    }

    @Test
    void rejectsInvalidBudgetConfiguration() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var properties = new ExpensiveRequestProperties();
            assertTrue(factory.getValidator().validate(properties).isEmpty());
            properties.setLimit(0);
            properties.setWindowSeconds(0);
            assertEquals(2, factory.getValidator().validate(properties).size());
        }
    }
}
