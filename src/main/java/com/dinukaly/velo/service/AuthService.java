package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.entity.User;

public interface AuthService {
    User register(RegisterRequestDTO dto);
    User authenticate(AuthDTO dto);
}
