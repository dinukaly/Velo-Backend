package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.*;
import com.dinukaly.velo.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class FileController {

    private final FileService fileService;

    // POST /api/v1/files/file — create a new file node
    @PostMapping("/file")
    public ResponseEntity<APIResponse> createFile(
            @RequestBody CreateFileRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "File created successfully",
                fileService.createFile(dto, userDetails.getUsername())));
    }

    // POST /api/v1/files/folder — create a new folder node
    @PostMapping("/folder")
    public ResponseEntity<APIResponse> createFolder(
            @RequestBody CreateFolderRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Folder created successfully",
                fileService.createFolder(dto, userDetails.getUsername())));
    }

    // DELETE /api/v1/files/{nodeId} — delete a file or folder node
    @DeleteMapping("/{nodeId}")
    public ResponseEntity<APIResponse> delete(
            @PathVariable UUID nodeId,
            @AuthenticationPrincipal UserDetails userDetails) {

        fileService.delete(nodeId, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(
                200,
                "Node deleted successfully",
                null));
    }

    // PATCH /api/v1/files/{nodeId}/rename — rename a file or folder
    @PatchMapping("/{nodeId}/rename")
    public ResponseEntity<APIResponse> rename(
            @PathVariable UUID nodeId,
            @RequestBody RenameRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Node renamed successfully",
                fileService.rename(nodeId, dto.getNewName(), userDetails.getUsername())));
    }

    // GET /api/v1/files/tree/{projectId} — fetch the project's full file tree
    @GetMapping("/tree/{projectId}")
    public ResponseEntity<APIResponse> getProjectTree(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "Project tree retrieved successfully",
                fileService.getProjectTree(projectId)));
    }

    // GET /api/v1/files/{nodeId}/content — read a file's content
    @GetMapping("/{nodeId}/content")
    public ResponseEntity<APIResponse> readFile(
            @PathVariable UUID nodeId) {

        return ResponseEntity.ok(new APIResponse(
                200,
                "File content retrieved successfully",
                fileService.readFile(nodeId)));
    }

    // PUT /api/v1/files/content — write (save) content to a file
    @PutMapping("/content")
    public ResponseEntity<APIResponse> writeFile(
            @RequestBody WriteFileRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        fileService.writeFile(dto, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(
                200,
                "File saved successfully",
                null));
    }
}
