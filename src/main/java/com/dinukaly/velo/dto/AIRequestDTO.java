package com.dinukaly.velo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIRequestDTO {

    @NotBlank(message = "Message is required")
    private String message;

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "Current file path is required")
    private String currentFilePath;

    private String selectedCode;
}
