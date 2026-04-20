package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.DuplicateResourceException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AuthService;
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

    @Override
    public User register(RegisterRequestDTO dto) {
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
        return user;
    }

    @Override
    public User authenticate(AuthDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new NotFoundException("Email Not Found"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new CustomAuthenticationException("Password Do Not Match");
        }
        return user;
    }
}
