package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.dto.AdminUpdateUserRequestDTO;
import com.dinukaly.velo.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<APIResponse> stats() {
        return ResponseEntity.ok(new APIResponse(
                200,
                "Admin stats retrieved successfully",
                adminService.getStats()
        ));
    }

    @GetMapping("/users")
    public ResponseEntity<APIResponse> users() {
        return ResponseEntity.ok(new APIResponse(
                200,
                "Admin user list retrieved successfully",
                adminService.listUsers()
        ));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<APIResponse> updateUser(
            @PathVariable UUID userId,
            @RequestBody AdminUpdateUserRequestDTO requestDTO,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(new APIResponse(
                200,
                "User updated successfully",
                adminService.updateUser(userId, requestDTO, userDetails.getUsername())
        ));
    }

    @GetMapping("/projects")
    public ResponseEntity<APIResponse> projects() {
        return ResponseEntity.ok(new APIResponse(
                200,
                "Admin project list retrieved successfully",
                adminService.listProjects()
        ));
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<APIResponse> deleteProject(@PathVariable UUID projectId) {
        adminService.deleteProject(projectId);
        return ResponseEntity.ok(new APIResponse(
                200,
                "Project deleted successfully",
                null
        ));
    }
}
