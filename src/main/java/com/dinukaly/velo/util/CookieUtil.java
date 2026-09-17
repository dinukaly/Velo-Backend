package com.dinukaly.velo.util;

import com.dinukaly.velo.config.CookieProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    private static final String ACCESS_COOKIE_NAME = "access_token";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String ACCESS_COOKIE_PATH = "/";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    private final CookieProperties cookieProperties;

    public CookieUtil(@Value("${jwt.access.expiration}") long accessExpirationMs,
                      @Value("${jwt.refresh.expiration}") long refreshExpirationMs,
                      CookieProperties cookieProperties) {
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.cookieProperties = cookieProperties;
    }

    /**
     * builds the HttpOnly access_token cookie
     */
    public ResponseCookie buildAccessCookie(String token) {
        return buildCookie(ACCESS_COOKIE_NAME, token, ACCESS_COOKIE_PATH)
            .httpOnly(true)
            .maxAge(Duration.ofMillis(accessExpirationMs))
            .build();
    }

    /**
     * builds the HttpOnly refresh_token cookie
     */
    public ResponseCookie buildRefreshCookie(String token) {
        return buildCookie(REFRESH_COOKIE_NAME, token, REFRESH_COOKIE_PATH)
            .httpOnly(true)
            .maxAge(Duration.ofMillis(refreshExpirationMs))
            .build();
    }

    /**
     * clear access_cookie when logout
     */
    public ResponseCookie clearAccessCookie() {
        return buildCookie(ACCESS_COOKIE_NAME, "", ACCESS_COOKIE_PATH)
                .httpOnly(true)
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * clear refresh_token when logout
     */
    public ResponseCookie clearRefreshCookie() {
        return buildCookie(REFRESH_COOKIE_NAME, "", REFRESH_COOKIE_PATH)
                .httpOnly(true)
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder buildCookie(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .secure(cookieProperties.isSecure())
                .path(path)
                .sameSite(cookieProperties.getSameSite().attributeValue());
    }
}
