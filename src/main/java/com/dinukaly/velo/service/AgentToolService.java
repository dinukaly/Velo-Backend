package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.FileContentResponseDTO;
import com.dinukaly.velo.dto.FsNodeDTO;
import com.dinukaly.velo.dto.agent.CodeSearchResultDTO;
import com.dinukaly.velo.dto.agent.FileRangeResponseDTO;
import com.dinukaly.velo.dto.git.GitDiffDTO;
import com.dinukaly.velo.dto.git.GitStatusDTO;

import java.util.List;
import java.util.UUID;

public interface AgentToolService {

    /**
     * List files/directories inside relativePath in the given project.
     */
    List<FsNodeDTO> listDirectory(UUID projectId, String relativePath, String email);

    /**
     * Read the full content of a single file, subject to path traversal & size limits.
     */
    FileContentResponseDTO readFile(UUID projectId, String relativePath, String email);

    /**
     * Read a specific 1-indexed line range from a file.
     */
    FileRangeResponseDTO readFileRange(UUID projectId, String relativePath, int startLine, int endLine, String email);

    /**
     * Fallback filesystem keyword search across readable code files in the project.
     */
    CodeSearchResultDTO searchCode(UUID projectId, String query, String email);

    /**
     * Get local Git repository status.
     */
    GitStatusDTO getGitStatus(UUID projectId, String email);

    /**
     * Get local Git diff for a path or full repository.
     */
    GitDiffDTO getGitDiff(UUID projectId, String path, boolean staged, String email);

    /**
     * Get top-level structure / summary of project files.
     */
    List<FsNodeDTO> getProjectStructure(UUID projectId, String email);
}
