package com.dinukaly.velo.util;

import com.dinukaly.velo.config.CookieProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieUtilTest {

    private CookieProperties properties;
    private CookieUtil cookieUtil;

    @BeforeEach
    void setUp() {
        properties = new CookieProperties();
        properties.setSecure(true);
        properties.setSameSite(CookieProperties.SameSitePolicy.NONE);
        cookieUtil = new CookieUtil(900_000, 604_800_000, properties);
    }

    @Test
    void issuedCookiesUseConfiguredSecurityAttributesAndMillisecondDurations() {
        ResponseCookie accessCookie = cookieUtil.buildAccessCookie("access-value");
        ResponseCookie refreshCookie = cookieUtil.buildRefreshCookie("refresh-value");

        assertTrue(accessCookie.isHttpOnly());
        assertTrue(accessCookie.isSecure());
        assertEquals("None", accessCookie.getSameSite());
        assertEquals("/", accessCookie.getPath());
        assertEquals(Duration.ofMinutes(15), accessCookie.getMaxAge());

        assertTrue(refreshCookie.isHttpOnly());
        assertTrue(refreshCookie.isSecure());
        assertEquals("None", refreshCookie.getSameSite());
        assertEquals("/api/v1/auth", refreshCookie.getPath());
        assertEquals(Duration.ofDays(7), refreshCookie.getMaxAge());
    }

    @Test
    void clearedCookiesPreserveIssuedCookieAttributes() {
        ResponseCookie clearAccess = cookieUtil.clearAccessCookie();
        ResponseCookie clearRefresh = cookieUtil.clearRefreshCookie();

        assertEquals("/", clearAccess.getPath());
        assertEquals("/api/v1/auth", clearRefresh.getPath());
        assertTrue(clearAccess.isSecure());
        assertTrue(clearRefresh.isSecure());
        assertEquals("None", clearAccess.getSameSite());
        assertEquals("None", clearRefresh.getSameSite());
        assertEquals(Duration.ZERO, clearAccess.getMaxAge());
        assertEquals(Duration.ZERO, clearRefresh.getMaxAge());
    }

    @Test
    void sameSiteNoneRequiresSecureCookies() {
        properties.setSecure(false);
        assertFalse(properties.isSameSiteNoneSecure());

        properties.setSecure(true);
        assertTrue(properties.isSameSiteNoneSecure());
    }
}
