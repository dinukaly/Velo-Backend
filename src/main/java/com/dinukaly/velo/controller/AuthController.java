package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.dto.AuthDetailsDTO;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AuthService;
import com.dinukaly.velo.service.custom.EmailService;
import com.dinukaly.velo.service.custom.EmailVerificationService;
import com.dinukaly.velo.service.custom.RefreshTokenService;
import com.dinukaly.velo.util.CookieUtil;
import com.dinukaly.velo.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/signup")
    public ResponseEntity<APIResponse> signup(@Valid @RequestBody RegisterRequestDTO dto) {
        AuthDetailsDTO principal = authService.register(dto);
        return ResponseEntity.ok(new APIResponse(
                200,
                "Registration successful. Please check your email to verify your account.",
                Map.of("email", principal.getEmail())
        ));
    }
    // email verification
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        try {
            String userId = emailVerificationService.consumeToken(token);

            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new NotFoundException("User not found"));

            user.setEnabled(true);
            userRepository.save(user);
            log.info("[Auth] Email verified for userId={}", userId);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/auth/verify-email/status?state=success"))
                    .build();

        } catch (Exception e) {
            log.warn("[Auth] Email verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/auth/verify-email/status?state=error"))
                    .build();
        }
    }

    /**
     * request a new verification email
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<APIResponse> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEnabled()) {
                // createVerificationToken enforces the 2 min rate limit
                try {
                    String rawToken = emailVerificationService.createVerificationToken(user.getId().toString());
                    emailService.sendVerificationEmail(user.getEmail(), user.getName(), rawToken);
                    log.info("[Auth] Resent verification email to: {}", email);
                } catch (Exception e) {
                    log.warn("[Auth] Rate limit hit or error sending verification email to {}: {}", email, e.getMessage());
                }
            }
        });

        // Always return success to prevent email enumeration
        return ResponseEntity.ok(new APIResponse(200, "If an account exists and is unverified, a verification email has been sent.", null));
    }

    @PostMapping("/signin")
    public ResponseEntity<APIResponse> signin(@Valid @RequestBody AuthDTO dto) {
        AuthDetailsDTO principal = authService.authenticate(dto);
        return buildAuthResponse(principal, "Signed in successfully");
    }

    /**
     * validates the refresh_token cookie
     */
    @PostMapping("/refresh")
    public ResponseEntity<APIResponse> refresh(HttpServletRequest request) {
        String rawRefreshToken = extractCookie(request, "refresh_token");
        if (rawRefreshToken == null) {
            throw new CustomAuthenticationException("Refresh token missing");
        }

        String email = refreshTokenService.getEmailByToken(rawRefreshToken);
        if (email == null) {
            log.warn("[Auth] Refresh token not found in Redis — possible reuse attempt");
            throw new CustomAuthenticationException("Invalid or expired refresh token");
        }

        // Fetch user from DB to get the current, authoritative role
        AuthDetailsDTO principal = userRepository.findByEmail(email)
                .map(AuthDetailsDTO::from)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));

        // rotate the refresh token
        String newRawRefreshToken = refreshTokenService.rotateRefreshToken(rawRefreshToken, email);
        String newAccessToken = jwtUtil.generateAccessToken(principal);

        ResponseCookie accessCookie = cookieUtil.buildAccessCookie(newAccessToken);
        ResponseCookie refreshCookie = cookieUtil.buildRefreshCookie(newRawRefreshToken);

        log.info("[Auth] Token rotated for: {}", email);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new APIResponse(200, "Token refreshed", null));
    }

    //Revokes the refresh token from Redis and clears both cookies
    @PostMapping("/logout")
    public ResponseEntity<APIResponse> logout(HttpServletRequest request) {
        String rawRefreshToken = extractCookie(request, "refresh_token");
        if (rawRefreshToken != null) {
            refreshTokenService.revokeToken(rawRefreshToken);
        }

        ResponseCookie clearAccess = cookieUtil.clearAccessCookie();
        ResponseCookie clearRefresh = cookieUtil.clearRefreshCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                .body(new APIResponse(200, "Logged out successfully", null));
    }

    /**
     * Generates both tokens, builds cookies, and wraps the response
     */
    private ResponseEntity<APIResponse> buildAuthResponse(AuthDetailsDTO principal, String message) {
        String accessToken = jwtUtil.generateAccessToken(principal);
        String rawRefreshToken = refreshTokenService.createRefreshToken(principal.getEmail());

        ResponseCookie accessCookie = cookieUtil.buildAccessCookie(accessToken);
        ResponseCookie refreshCookie = cookieUtil.buildRefreshCookie(rawRefreshToken);

        // Return user info in the body
        Map<String, String> userInfo = Map.of(
                "id", principal.getId() != null ? principal.getId().toString() : "",
                "name", principal.getName() != null ? principal.getName() : "",
                "email", principal.getEmail()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new APIResponse(200, message, userInfo));
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}