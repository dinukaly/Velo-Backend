package com.dinukaly.velo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class FsCreateRequestDTO {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    private String parentPath;

    @NotBlank(message = "Name is required")
    private String name;
}
