package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.AuthResponseDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;

public interface AuthService {
    AuthResponseDTO register(RegisterRequestDTO registerRequestDTO);
    AuthResponseDTO authenticate(RegisterRequestDTO registerRequestDTO);
}
