package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.dto.git.GitBranchCreateRequestDTO;
import com.dinukaly.velo.dto.git.GitCheckoutRequestDTO;
import com.dinukaly.velo.dto.git.GitCommitRequestDTO;
import com.dinukaly.velo.dto.git.GitStageRequestDTO;
import com.dinukaly.velo.service.GitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/git/{projectId}")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class GitController {

    private final GitService gitService;

    @PostMapping("/init")
    public ResponseEntity<APIResponse> initRepository(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git repository initialized successfully",
                gitService.initRepository(projectId, userDetails.getUsername())));
    }

    @GetMapping("/status")
    public ResponseEntity<APIResponse> status(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git status retrieved successfully",
                gitService.status(projectId, userDetails.getUsername())));
    }

    @GetMapping("/diff")
    public ResponseEntity<APIResponse> diff(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "false") boolean staged,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git diff retrieved successfully",
                gitService.diff(projectId, path, staged, userDetails.getUsername())));
    }

    @PostMapping("/stage")
    public ResponseEntity<APIResponse> stage(
            @PathVariable UUID projectId,
            @RequestBody(required = false) GitStageRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git changes staged successfully",
                gitService.stage(projectId, dto, userDetails.getUsername())));
    }

    @PostMapping("/unstage")
    public ResponseEntity<APIResponse> unstage(
            @PathVariable UUID projectId,
            @RequestBody(required = false) GitStageRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git changes unstaged successfully",
                gitService.unstage(projectId, dto, userDetails.getUsername())));
    }

    @PostMapping("/commit")
    public ResponseEntity<APIResponse> commit(
            @PathVariable UUID projectId,
            @Valid @RequestBody GitCommitRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git commit created successfully",
                gitService.commit(projectId, dto, userDetails.getUsername())));
    }

    @GetMapping("/log")
    public ResponseEntity<APIResponse> log(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git log retrieved successfully",
                gitService.log(projectId, limit, userDetails.getUsername())));
    }

    @GetMapping("/branches")
    public ResponseEntity<APIResponse> branches(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git branches retrieved successfully",
                gitService.branches(projectId, userDetails.getUsername())));
    }

    @PostMapping("/branches")
    public ResponseEntity<APIResponse> createBranch(
            @PathVariable UUID projectId,
            @Valid @RequestBody GitBranchCreateRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git branch created successfully",
                gitService.createBranch(projectId, dto, userDetails.getUsername())));
    }

    @PostMapping("/checkout")
    public ResponseEntity<APIResponse> checkout(
            @PathVariable UUID projectId,
            @Valid @RequestBody GitCheckoutRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Git branch checked out successfully",
                gitService.checkout(projectId, dto, userDetails.getUsername())));
    }
}
