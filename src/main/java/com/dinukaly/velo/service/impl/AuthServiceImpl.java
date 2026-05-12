package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.dto.AuthDetailsDTO;
import com.dinukaly.velo.dto.ForgotPasswordRequestDTO;
import com.dinukaly.velo.dto.ResetPasswordRequestDTO;
import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.DuplicateResourceException;
import com.dinukaly.velo.exception.EmailNotVerifiedException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AuthService;
import com.dinukaly.velo.service.custom.EmailService;
import com.dinukaly.velo.service.custom.EmailVerificationService;
import com.dinukaly.velo.service.custom.LoginAttemptService;
import com.dinukaly.velo.service.custom.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;
    private final PasswordResetService passwordResetService;

    @Override
    public AuthDetailsDTO register(RegisterRequestDTO dto) {
        User existingUser = userRepository.findByEmail(dto.getEmail()).orElse(null);
        if (existingUser != null) {
            if (existingUser.isEnabled()) {
                throw new DuplicateResourceException("An account with this email already exists. Please sign in.");
            }

            try {
                String rawToken = emailVerificationService.createVerificationToken(existingUser.getId().toString());
                emailService.sendVerificationEmail(existingUser.getEmail(), existingUser.getName(), rawToken);
                log.info("[Auth] Resent verification during signup for existing unverified user: {}", existingUser.getEmail());
            } catch (Exception e) {
                log.warn("[Auth] Signup retry for unverified user {} hit resend cooldown or email issue: {}",
                        existingUser.getEmail(), e.getMessage());
            }

            return AuthDetailsDTO.from(existingUser);
        }

        User user = User.builder()
                .email(dto.getEmail())
                .name(dto.getName())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(Role.USER)
                .build();
        userRepository.save(user);
        log.info("[Auth] Registered new user (unverified): {}", user.getEmail());

        // send verification email
        String rawToken = emailVerificationService.createVerificationToken(user.getId().toString());
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), rawToken);

        return AuthDetailsDTO.from(user);
    }

    @Override
    public AuthDetailsDTO authenticate(AuthDTO dto) {
        // Brute force check
        loginAttemptService.checkBlocked(dto.getEmail());

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(dto.getEmail());
                    return new CustomAuthenticationException("Invalid email or password");
                });

        // Validate password
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(dto.getEmail());
            throw new CustomAuthenticationException("Invalid email or password");
        }

        // Check email verification
        if (!user.isEnabled()) {
            log.warn("[Auth] Login attempt by unverified user: {}", user.getEmail());
            throw new EmailNotVerifiedException("Please verify your email address before logging in.", user.getEmail());
        }

        loginAttemptService.clearFailures(dto.getEmail());
        log.info("[Auth] User authenticated: {}", user.getEmail());

        return AuthDetailsDTO.from(user);
    }

    @Override
    public void requestPasswordReset(ForgotPasswordRequestDTO dto) {
        userRepository.findByEmail(dto.getEmail()).ifPresent(user -> {
            try {
                String rawToken = passwordResetService.createResetToken(user.getId().toString());
                emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), rawToken);
                log.info("[Auth] Password reset email queued for {}", user.getEmail());
            } catch (Exception e) {
                log.warn("[Auth] Password reset request for {} hit cooldown or email issue: {}",
                        user.getEmail(), e.getMessage());
            }
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequestDTO dto) {
        String userId = passwordResetService.consumeToken(dto.getToken());

        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        userRepository.save(user);
        loginAttemptService.clearFailures(user.getEmail());

        log.info("[Auth] Password reset completed for {}", user.getEmail());
    }
}
