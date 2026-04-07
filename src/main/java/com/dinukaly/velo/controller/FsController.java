package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.*;
import com.dinukaly.velo.service.FsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class FsController {

    private final FsService fsService;

    /**
     * listing directry
     */
    @GetMapping("/tree/{projectId}")
    public ResponseEntity<APIResponse> listDirectory(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String path,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Directory listed successfully",
                fsService.listDirectory(projectId, path, userDetails.getUsername())));
    }

    /**
     * create a file
     */
    @PostMapping("/file")
    public ResponseEntity<APIResponse> createFile(
            @Valid @RequestBody FsCreateRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "File created successfully",
                fsService.createFile(dto, userDetails.getUsername())));
    }

    /**
     * create a folder
     */
    @PostMapping("/folder")
    public ResponseEntity<APIResponse> createFolder(
            @Valid @RequestBody FsCreateRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Folder created successfully",
                fsService.createFolder(dto, userDetails.getUsername())));
    }

    /**
     * delete folder or a directory
     */
    @DeleteMapping
    public ResponseEntity<APIResponse> delete(
            @RequestParam UUID projectId,
            @RequestParam String path,
            @AuthenticationPrincipal UserDetails userDetails) {

        fsService.delete(projectId, path, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Deleted successfully", null));
    }

    /**
     * rename
     */
    @PutMapping("/rename")
    public ResponseEntity<APIResponse> rename(
            @Valid @RequestBody FsRenameRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Renamed successfully",
                fsService.rename(dto, userDetails.getUsername())));
    }

    /**
     * read the file content
     */
    @GetMapping("/content")
    public ResponseEntity<APIResponse> readFile(
            @RequestParam UUID projectId,
            @RequestParam String path) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "File content retrieved successfully",
                fsService.readFile(projectId, path)));
    }

    /**
     * write file
     */
    @PutMapping("/content")
    public ResponseEntity<APIResponse> writeFile(
            @Valid @RequestBody FsWriteRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        fsService.writeFile(dto, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "File saved successfully", null));
    }
}
