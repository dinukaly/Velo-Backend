package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.AuthResponseDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AuthService;
import com.dinukaly.velo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public AuthResponseDTO register(RegisterRequestDTO registerRequestDTO) {

        if(userRepository.existsByEmail(registerRequestDTO.getEmail())){
            throw new RuntimeException("Email Already Exists");
        }
        User user = User.builder()
                .email(registerRequestDTO.getEmail())
                .name(registerRequestDTO.getName())
                .passwordHash(passwordEncoder.encode(registerRequestDTO.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);
        log.info("User {} has been registered", user.getName());
        String token = jwtUtil.generateToken(user);
        return new AuthResponseDTO(token);
    }

    @Override
    public AuthResponseDTO authenticate(AuthDTO authDTO) {
        User user = userRepository.findByEmail(authDTO.getEmail())
                .orElseThrow(() -> new RuntimeException("Email Not Found"));

        //validate password
        if (!passwordEncoder.matches(authDTO.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Password Do Not Match");
        }
        //generate token
        String token = jwtUtil.generateToken(user);
        return new AuthResponseDTO(token);
    }
}
