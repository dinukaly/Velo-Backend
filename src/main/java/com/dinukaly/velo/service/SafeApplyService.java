package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.agent.ApplyResultDTO;

import java.util.UUID;

public interface SafeApplyService {

    /**
     * Applies all ACCEPTED hunks in the proposal for the given run to the project workspace.
     *
     * @param runId     The agent run UUID
     * @param userEmail Authenticated user email (used for ownership validation)
     * @return Structured result summarizing which files were applied, skipped, or failed
     */
    ApplyResultDTO apply(UUID runId, String userEmail);
}
