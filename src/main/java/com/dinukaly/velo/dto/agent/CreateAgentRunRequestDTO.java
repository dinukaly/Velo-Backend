package com.dinukaly.velo.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateAgentRunRequestDTO {

    @NotNull(message = "projectId is required")
    private UUID projectId;

    @NotBlank(message = "message is required")
    @Size(max = 4000, message = "message must not exceed 4000 characters")
    private String message;

    // Path of the currently active editor tab (project-relative)
    private String currentPath;

    // The highlighted/selected text in the editor, if any
    private String selectedText;

    // All open tab paths at the time of request creation
    private List<String> openFiles;

    // Paths of tabs with unsaved changes (run will be blocked if non-empty in V1)
    private List<String> dirtyFiles;
}
