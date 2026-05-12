package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.dto.CreateProjectRequestDTO;
import com.dinukaly.velo.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping("/create")
    public ResponseEntity<APIResponse> create(
            @Valid @RequestBody CreateProjectRequestDTO requestDTO,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Creating project with request: {}", requestDTO.toString());
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
    @GetMapping("/{projectId}")
    public ResponseEntity<APIResponse> getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                new APIResponse(
                        200,
                        "Project retrieved successfully",
                        projectService.getProjectById(projectId, userDetails.getUsername())
                )
        );
    }
    @DeleteMapping("/delete/{projectId}")
    public ResponseEntity<APIResponse> delete(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        projectService.deleteProject(projectId, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(
                        200,
                        "Project deleted successfully",
                        null));
    }
}