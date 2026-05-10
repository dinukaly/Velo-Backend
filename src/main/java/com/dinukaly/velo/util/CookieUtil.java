package com.dinukaly.velo.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${jwt.access.expiration}")
    private long accessExpirationMs;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpirationSeconds;

    private static final boolean SECURE = false;

    /**
     * builds the HttpOnly access_token cookie
     */
    public ResponseCookie buildAccessCookie(String token) {
        return ResponseCookie.from("access_token", token)
            .httpOnly(true)
            .secure(SECURE)
            .path("/")
            .maxAge(accessExpirationMs / 1000) 
            .sameSite("Lax")
            .build();
    }

    /**
     * builds the HttpOnly refresh_token cookie
     */
    public ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from("refresh_token", token)
            .httpOnly(true)
            .secure(SECURE)
            .path("/api/v1/auth")
            .maxAge(refreshExpirationSeconds)
            .sameSite("Lax")
            .build();
    }

    /**
     * clear access_cookie when logout
     */
    public ResponseCookie clearAccessCookie() {
        return ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(SECURE)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    /**
     * clear refresh_token when logout
     */
    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(SECURE)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }
}
