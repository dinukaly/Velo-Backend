package com.dinukaly.velo.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Extract token from Cookie, Authorization Header, or Query Parameter (for SSE / WebSockets)
        String jwtToken = extractJwtToken(request);

        if (jwtToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = jwtUtil.getEmailFromToken(jwtToken);
                if (email != null && jwtUtil.validateToken(jwtToken)) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Invalid or expired token - allow request to proceed to SecurityContext entry point
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Cookie, Authorization header, or Query parameters.
     */
    private String extractJwtToken(HttpServletRequest request) {
        // 1. Try Cookie
        String token = extractFromCookie(request, "access_token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        // 2. Try Authorization Header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 3. Try Query Parameter (essential for browser EventSource SSE connections)
        String queryToken = request.getParameter("token");
        if (queryToken == null || queryToken.isBlank()) {
            queryToken = request.getParameter("access_token");
        }
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }

        return null;
    }

    /**
     * extract cookie value from the request by name
     */
    private String extractFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
