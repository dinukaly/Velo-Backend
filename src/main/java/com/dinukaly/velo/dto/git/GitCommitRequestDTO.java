package com.dinukaly.velo.dto.git;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GitCommitRequestDTO {
    @NotBlank(message = "Commit message is required")
    private String message;
}
