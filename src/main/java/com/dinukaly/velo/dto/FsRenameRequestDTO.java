package com.dinukaly.velo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class FsRenameRequestDTO {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "Current path is required")
    private String path;

    @NotBlank(message = "New name is required")
    private String newName;
}
