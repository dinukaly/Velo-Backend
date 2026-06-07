package com.dinukaly.velo.dto.git;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GitBranchCreateRequestDTO {
    @NotBlank(message = "Branch name is required")
    private String name;

    private boolean checkout;
}
