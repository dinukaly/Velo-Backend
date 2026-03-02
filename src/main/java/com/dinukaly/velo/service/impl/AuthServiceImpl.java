package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.AuthResponseDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AuthService;
import com.dinukaly.velo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public AuthResponseDTO register(RegisterRequestDTO registerRequestDTO) {

        if(userRepository.existsByEmail(registerRequestDTO.getEmail())){
            throw new RuntimeException("Email Already Exists");
        }
        if (userRepository.existsByUsername(registerRequestDTO.getUsername())) {
            throw new RuntimeException("Username Already Exists");
        }
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(registerRequestDTO.getEmail())
                .username(registerRequestDTO.getUsername())
                .passwordHash(passwordEncoder.encode(registerRequestDTO.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());
        return new AuthResponseDTO(token);
    }

    @Override
    public AuthResponseDTO authenticate(RegisterRequestDTO registerRequestDTO) {
        return null;
    }
}
