package com.dinukaly.velo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateFileRequestDTO {
    @NotNull(message = "Project ID is required")
    private UUID projectId;
    
    private UUID parentId;
    
    @NotBlank(message = "File name is required")
    @Size(max = 100, message = "File name must not exceed 100 characters")
    private String name;
}
