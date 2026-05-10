package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.dto.AuthDetailsDTO;
import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.DuplicateResourceException;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AuthService;
import com.dinukaly.velo.service.custom.LoginAttemptService;
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

    @Override
    public AuthDetailsDTO register(RegisterRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Email Already Exists");
        }
        User user = User.builder()
                .email(dto.getEmail())
                .name(dto.getName())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .build();
        userRepository.save(user);
        log.info("[Auth] Registered new user: {}", user.getEmail());

        return AuthDetailsDTO.from(user);
    }

    @Override
    public AuthDetailsDTO authenticate(AuthDTO dto) {
        // 1. brute-force check
        loginAttemptService.checkBlocked(dto.getEmail());

        // 2. lookup user
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(dto.getEmail());
                    return new CustomAuthenticationException("Invalid email or password");
                });

        // 3. validate password
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(dto.getEmail());
            throw new CustomAuthenticationException("Invalid email or password");
        }

        // 4. clear any previous failure counter
        loginAttemptService.clearFailures(dto.getEmail());
        log.info("[Auth] User authenticated: {}", user.getEmail());

        return AuthDetailsDTO.from(user);
    }
}
