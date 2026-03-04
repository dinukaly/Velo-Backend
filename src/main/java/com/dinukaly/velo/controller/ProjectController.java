package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.dto.CreateProjectRequestDTO;
import com.dinukaly.velo.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping("/create")
    public ResponseEntity<APIResponse> create(
            @RequestBody CreateProjectRequestDTO requestDTO,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Project created successfully",
                projectService.createProject(requestDTO, userDetails.getUsername()))
        );
    }

    @GetMapping("/list")
    public ResponseEntity<APIResponse> list(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                        200,
                        "Project list retrieved successfully",
                projectService.listProjects(userDetails.getUsername()))
        );
    }
}