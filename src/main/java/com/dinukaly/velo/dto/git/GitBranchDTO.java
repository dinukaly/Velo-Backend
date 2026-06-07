package com.dinukaly.velo.dto.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitBranchDTO {
    private String currentBranch;
    private List<String> branches;
}
