package com.dinukaly.velo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ExpensiveRequestInterceptor implements HandlerInterceptor {
    private final ExpensiveRequestLimiter limiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(ExpensiveRequest.class)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            reject(response, 401, "Authentication required", 0);
            return false;
        }
        long retry;
        try {
            // Never trust headers, query parameters, project IDs, or client-supplied account names.
            retry = limiter.admit(authentication.getName());
        } catch (RuntimeException ex) {
            reject(response, 503, "Request admission temporarily unavailable", 5);
            return false;
        }
        if (retry > 0) {
            reject(response, 429, "Expensive request limit reached", retry);
            return false;
        }
        return true;
    }

    private void reject(HttpServletResponse response, int status, String message, long retry) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (retry > 0) response.setHeader("Retry-After", Long.toString(retry));
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
