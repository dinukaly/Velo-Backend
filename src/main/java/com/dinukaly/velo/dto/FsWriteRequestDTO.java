package com.dinukaly.velo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class FsWriteRequestDTO {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "File path is required")
    private String path;

    private String content;
}
