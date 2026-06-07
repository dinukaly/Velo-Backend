package com.dinukaly.velo.dto.git;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GitCheckoutRequestDTO {
    @NotBlank(message = "Branch name is required")
    private String branch;
}
