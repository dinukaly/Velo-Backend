package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.dto.AuthDTO;
import com.dinukaly.velo.dto.AuthResponseDTO;
import com.dinukaly.velo.dto.RegisterRequestDTO;
import com.dinukaly.velo.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<APIResponse> signup(@RequestBody RegisterRequestDTO registerRequestDTO) {
        return ResponseEntity.ok(
                new APIResponse(
                        200,
                        "User " + registerRequestDTO.getName() + " has been registered",
                        authService.register(registerRequestDTO)
                )
        );
    }

    @PostMapping("/signin")
    public ResponseEntity<APIResponse> signin(@RequestBody AuthDTO authDTO) {
        return ResponseEntity.ok(
                new APIResponse(
                        200,
                        "user logged in successfully",
                        authService.authenticate(authDTO)
                )
        );
    }
}
