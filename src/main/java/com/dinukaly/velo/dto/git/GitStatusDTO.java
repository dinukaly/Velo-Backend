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
public class GitStatusDTO {
    private boolean repositoryInitialized;
    private String currentBranch;
    private boolean clean;
    private List<GitFileChangeDTO> stagedChanges;
    private List<GitFileChangeDTO> unstagedChanges;
    private List<GitFileChangeDTO> untrackedFiles;
    private List<GitFileChangeDTO> conflictingFiles;
}
