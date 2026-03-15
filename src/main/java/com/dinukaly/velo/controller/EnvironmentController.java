package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.environment.EnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/environment")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    /**
     * POST /api/v1/environment/open/{projectId}
     */
    @PostMapping("/open/{projectId}")
    public ResponseEntity<APIResponse> openEnvironment(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Environment started successfully",
                environmentService.prepareEnvironment(projectId, userDetails.getUsername())
        ));
    }
}
