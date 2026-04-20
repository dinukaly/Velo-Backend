package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.dto.AuthDetailsDTO;

public interface AuthService {
    AuthDetailsDTO register(RegisterRequestDTO dto);
    AuthDetailsDTO authenticate(AuthDTO dto);
}
