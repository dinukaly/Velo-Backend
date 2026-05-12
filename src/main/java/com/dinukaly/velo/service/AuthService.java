package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.dto.AuthDetailsDTO;
import com.dinukaly.velo.dto.ForgotPasswordRequestDTO;
import com.dinukaly.velo.dto.ResetPasswordRequestDTO;

public interface AuthService {
    AuthDetailsDTO register(RegisterRequestDTO dto);
    AuthDetailsDTO authenticate(AuthDTO dto);
    void requestPasswordReset(ForgotPasswordRequestDTO dto);
    void resetPassword(ResetPasswordRequestDTO dto);
}
