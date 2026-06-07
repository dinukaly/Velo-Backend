package com.dinukaly.velo.dto.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitDiffDTO {
    private String path;
    private boolean staged;
    private String diff;
    private boolean truncated;
}
