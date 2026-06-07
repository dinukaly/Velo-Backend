package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.git.*;

import java.util.List;
import java.util.UUID;

public interface GitService {
    GitStatusDTO initRepository(UUID projectId, String email);

    GitStatusDTO status(UUID projectId, String email);

    GitDiffDTO diff(UUID projectId, String path, boolean staged, String email);

    GitStatusDTO stage(UUID projectId, GitStageRequestDTO dto, String email);

    GitStatusDTO unstage(UUID projectId, GitStageRequestDTO dto, String email);

    GitCommitDTO commit(UUID projectId, GitCommitRequestDTO dto, String email);

    List<GitCommitDTO> log(UUID projectId, int limit, String email);

    GitBranchDTO branches(UUID projectId, String email);

    GitBranchDTO createBranch(UUID projectId, GitBranchCreateRequestDTO dto, String email);

    GitBranchDTO checkout(UUID projectId, GitCheckoutRequestDTO dto, String email);
}
